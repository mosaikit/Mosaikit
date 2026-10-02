// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.core.teams;

import dev.mosaikit.kernel.api.teams.Teams;
import dev.mosaikit.kernel.core.account.AccountDirectory;
import dev.mosaikit.kernel.core.account.UserAccount;
import dev.mosaikit.kernel.core.account.UserAccounts;
import dev.mosaikit.kernel.core.audit.AuditEvent;
import dev.mosaikit.kernel.core.audit.AuditLog;
import dev.mosaikit.kernel.core.error.ConflictException;
import dev.mosaikit.kernel.core.error.ForbiddenOperationException;
import dev.mosaikit.kernel.core.error.InvalidInputException;
import dev.mosaikit.kernel.core.error.ResourceNotFoundException;
import dev.mosaikit.kernel.core.identity.RequestOrganization;
import dev.mosaikit.kernel.core.organization.OrganizationMember;
import dev.mosaikit.kernel.core.organization.OrganizationMembers;
import dev.mosaikit.kernel.core.security.Roles;
import io.agroal.api.AgroalDataSource;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The teams of organizations (MK-032). An owner of a team, or an administrator of the organization,
 * manages it; a member of the organization joins a public team; a guest is a person with an account
 * of the installation, admitted to the organization only as {@value Roles#ORGANIZATION_GUEST} and
 * only for the teams where they were added. Every change is in the audit.
 */
@ApplicationScoped
@Transactional
public class TeamService implements Teams {

    static final int MAX_NAME = 100;
    static final int MAX_DESCRIPTION = 500;

    private final TeamRepository teams;
    private final OrganizationMembers organizationMembers;
    private final UserAccounts accounts;
    private final AccountDirectory directory;
    private final RequestOrganization organization;
    private final SecurityIdentity identity;
    private final AuditLog audit;
    private final AgroalDataSource dataSource;
    private final String accountsOfTeam;
    private final Clock clock;

    @Inject
    public TeamService(
            TeamRepository teams,
            OrganizationMembers organizationMembers,
            UserAccounts accounts,
            AccountDirectory directory,
            RequestOrganization organization,
            SecurityIdentity identity,
            AuditLog audit,
            AgroalDataSource dataSource,
            @ConfigProperty(name = "quarkus.hibernate-orm.database.default-schema", defaultValue = "mk_kernel")
                    String kernelSchema) {
        this(
                teams,
                organizationMembers,
                accounts,
                directory,
                organization,
                identity,
                audit,
                dataSource,
                kernelSchema,
                Clock.systemUTC());
    }

    TeamService(
            TeamRepository teams,
            OrganizationMembers organizationMembers,
            UserAccounts accounts,
            AccountDirectory directory,
            RequestOrganization organization,
            SecurityIdentity identity,
            AuditLog audit,
            AgroalDataSource dataSource,
            String kernelSchema,
            Clock clock) {
        this.dataSource = dataSource;
        // The schema is a quoted identifier, which cannot end it nor add SQL.
        this.accountsOfTeam =
                "select account_id from \"" + kernelSchema.replace("\"", "\"\"") + "\".team_member where team_id = ?";
        this.teams = teams;
        this.organizationMembers = organizationMembers;
        this.accounts = accounts;
        this.directory = directory;
        this.organization = organization;
        this.identity = identity;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * A team as the API returns it.
     *
     * @param role the role of the person of the request, or {@code null}
     * @param members its people, when one team is asked for; {@code null} in lists
     */
    public record TeamView(
            UUID id, String name, String description, String visibility, String role, List<Member> members) {}

    /** What a team is made of, to create or change it. */
    public record TeamRequest(String name, String description, String visibility) {}

    @Override
    public List<Team> visible() {
        return list().stream()
                .map(team -> new Team(team.id(), team.name(), team.description(), team.visibility(), team.role()))
                .toList();
    }

    /** The teams that the person of the request sees, by name. */
    public List<TeamView> list() {
        UUID org = organization.require();
        UUID me = me();
        Map<UUID, String> mine = teams.membershipsIn(me, org).stream()
                .collect(Collectors.toMap(TeamMember::getTeamId, TeamMember::getRole));
        boolean guest = organization.guest();
        boolean admin = administers();
        return teams.ofOrganization(org).stream()
                .filter(team -> mine.containsKey(team.getId()) || (!guest && (team.isPublic() || admin)))
                .map(team -> view(team, mine.get(team.getId()), null))
                .toList();
    }

    /** A team that the person of the request sees, with its people. */
    public TeamView get(UUID id) {
        var team = seen(id);
        return view(team, roleOf(team.getId(), me()), members(team));
    }

    public TeamView create(TeamRequest request) {
        UUID org = organization.require();
        if (organization.guest()) {
            throw new ForbiddenOperationException("A guest of the organization cannot create teams.");
        }
        TeamRequest valid = checked(request, org, null);
        var team = new dev.mosaikit.kernel.core.teams.Team(
                org, valid.name(), valid.description(), valid.visibility(), actor(), clock.instant());
        teams.insert(team);
        teams.insert(new TeamMember(team.getId(), me(), TeamMember.OWNER, clock.instant()));
        audited("team.created", team, Map.of("name", team.getName(), "visibility", team.getVisibility()));
        return get(team.getId());
    }

    public TeamView change(UUID id, TeamRequest request) {
        var team = managed(id);
        TeamRequest valid = checked(request, team.getOrganizationId(), team);
        team.change(valid.name(), valid.description(), valid.visibility());
        teams.update(team);
        audited("team.changed", team, Map.of("name", team.getName(), "visibility", team.getVisibility()));
        return get(id);
    }

    public void delete(UUID id) {
        var team = managed(id);
        List<TeamMember> people = teams.members(id);
        for (TeamMember member : people) {
            teams.delete(member);
        }
        teams.delete(team);
        for (TeamMember member : people) {
            if (TeamMember.GUEST.equals(member.getRole())) {
                dismissGuest(member.getAccountId(), team.getOrganizationId());
            }
        }
        audited("team.deleted", team, Map.of("name", team.getName()));
    }

    /**
     * Adds a person to a team, or changes their role. A member of the organization can be owner or
     * member; a guest is a person with an account who is not a member of the organization, and
     * becomes its guest. A member of the organization joins a public team as member by themselves.
     */
    public TeamView putMember(UUID id, String email, String role) {
        if (role == null || !TeamMember.ROLES.contains(role)) {
            throw new InvalidInputException(List.of(
                    "role must be one of " + TeamMember.ROLES.stream().sorted().toList()));
        }
        var team = seen(id);
        UserAccount person = accounts.findByUsername(normalize(email))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account for " + email + ": the person signs up on this installation first."));
        boolean joining = person.getId().equals(me())
                && team.isPublic()
                && TeamMember.MEMBER.equals(role)
                && roleOf(id, me()) == null;
        if (!joining && !manages(team)) {
            throw new ForbiddenOperationException("Only the owners of the team manage its people.");
        }
        Optional<OrganizationMember> inOrganization =
                organizationMembers.findByAccountIdAndOrganizationId(person.getId(), team.getOrganizationId());
        boolean member = inOrganization
                .map(found -> found.getRoles().contains(Roles.ORGANIZATION_USER)
                        || found.getRoles().contains(Roles.ORGANIZATION_ADMIN))
                .orElse(false);
        if (TeamMember.GUEST.equals(role) && member) {
            throw new InvalidInputException(List.of(email + " is a member of the organization: add them as member"));
        }
        if (!TeamMember.GUEST.equals(role) && !member) {
            throw new InvalidInputException(List.of(email + " is not a member of the organization: add them as guest"));
        }
        Optional<TeamMember> existing = teams.member(id, person.getId());
        if (existing.isPresent()) {
            if (TeamMember.OWNER.equals(existing.get().getRole()) && !TeamMember.OWNER.equals(role)) {
                requireAnotherOwner(id, person.getId());
            }
            existing.get().changeRole(role);
            teams.update(existing.get());
        } else {
            if (TeamMember.GUEST.equals(role) && inOrganization.isEmpty()) {
                organizationMembers.insert(new OrganizationMember(
                        person.getId(), team.getOrganizationId(), Set.of(Roles.ORGANIZATION_GUEST), clock.instant()));
            }
            teams.insert(new TeamMember(id, person.getId(), role, clock.instant()));
        }
        audited("team.member.put", team, Map.of("email", person.getUsername(), "role", role));
        return get(id);
    }

    /** Removes a person from a team: an owner removes anyone, a person leaves by themselves. */
    public void removeMember(UUID id, String email) {
        var team = seen(id);
        UserAccount person = accounts.findByUsername(normalize(email))
                .orElseThrow(() -> new ResourceNotFoundException("No " + email + " in the team."));
        if (!person.getId().equals(me()) && !manages(team)) {
            throw new ForbiddenOperationException("Only the owners of the team manage its people.");
        }
        TeamMember member = teams.member(id, person.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No " + email + " in the team."));
        if (TeamMember.OWNER.equals(member.getRole())) {
            requireAnotherOwner(id, person.getId());
        }
        teams.delete(member);
        if (TeamMember.GUEST.equals(member.getRole())) {
            dismissGuest(person.getId(), team.getOrganizationId());
        }
        audited("team.member.removed", team, Map.of("email", person.getUsername()));
    }

    @Override
    public List<Member> members(UUID team) {
        UUID org = organization.require();
        return teams.findById(team)
                .filter(found -> found.getOrganizationId().equals(org) && sees(found))
                .map(this::members)
                .orElse(List.of());
    }

    @Override
    public boolean isMember(UUID team, UUID account) {
        return teams.member(team, account).isPresent();
    }

    /**
     * The accounts of a team, for the real-time channel, which delivers events outside requests:
     * read with JDBC, which needs no request.
     */
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public Set<UUID> accountsOf(UUID team) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(accountsOfTeam)) {
            statement.setObject(1, team);
            Set<UUID> result = new HashSet<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(rows.getObject(1, UUID.class));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read the people of the team " + team, e);
        }
    }

    /**
     * Refuses a team that the person of the request is not in, as if it did not exist: for the
     * documents shared with a team (MK-032).
     */
    public void requireIn(UUID team) {
        UUID org = organization.require();
        boolean in = teams.findById(team)
                        .filter(found -> found.getOrganizationId().equals(org))
                        .isPresent()
                && isMember(team, me());
        if (!in) {
            throw new ResourceNotFoundException("You are not in the team " + team + ".");
        }
    }

    private List<Member> members(dev.mosaikit.kernel.core.teams.Team team) {
        List<Member> result = new ArrayList<>();
        for (TeamMember member : teams.members(team.getId())) {
            accounts.findById(member.getAccountId())
                    .ifPresent(account -> result.add(new Member(
                            account.getId(), account.getUsername(), account.getDisplayName(), member.getRole())));
        }
        result.sort(Comparator.comparing(Member::role, Comparator.comparing(TeamService::rank))
                .thenComparing(Member::displayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static int rank(String role) {
        return switch (role) {
            case TeamMember.OWNER -> 0;
            case TeamMember.MEMBER -> 1;
            default -> 2;
        };
    }

    /** A guest who is in no team of the organization any more is no longer its guest. */
    private void dismissGuest(UUID account, UUID org) {
        if (!teams.membershipsIn(account, org).isEmpty()) {
            return;
        }
        organizationMembers
                .findByAccountIdAndOrganizationId(account, org)
                .filter(found -> Set.of(Roles.ORGANIZATION_GUEST).equals(found.getRoles()))
                .ifPresent(organizationMembers::delete);
    }

    private void requireAnotherOwner(UUID team, UUID account) {
        boolean another = teams.members(team).stream()
                .anyMatch(member -> TeamMember.OWNER.equals(member.getRole())
                        && !member.getAccountId().equals(account));
        if (!another) {
            throw new ConflictException("A team needs an owner: make someone else owner first.");
        }
    }

    /** A team of the organization of the request that the person sees; otherwise it does not exist. */
    private dev.mosaikit.kernel.core.teams.Team seen(UUID id) {
        UUID org = organization.require();
        return teams.findById(id)
                .filter(team -> team.getOrganizationId().equals(org) && sees(team))
                .orElseThrow(() -> new ResourceNotFoundException("No team " + id + "."));
    }

    private boolean sees(dev.mosaikit.kernel.core.teams.Team team) {
        if (roleOf(team.getId(), me()) != null) {
            return true;
        }
        return !organization.guest() && (team.isPublic() || administers());
    }

    private dev.mosaikit.kernel.core.teams.Team managed(UUID id) {
        var team = seen(id);
        if (!manages(team)) {
            throw new ForbiddenOperationException("Only the owners of the team manage it.");
        }
        return team;
    }

    private boolean manages(dev.mosaikit.kernel.core.teams.Team team) {
        return administers() || TeamMember.OWNER.equals(roleOf(team.getId(), me()));
    }

    /** Whether the person administers the organization of the request. */
    private boolean administers() {
        return identity.hasRole(Roles.ORGANIZATION_ADMIN);
    }

    private String roleOf(UUID team, UUID account) {
        return teams.member(team, account).map(TeamMember::getRole).orElse(null);
    }

    private TeamRequest checked(TeamRequest request, UUID org, dev.mosaikit.kernel.core.teams.Team current) {
        List<String> problems = new ArrayList<>();
        String name =
                request == null || request.name() == null ? "" : request.name().strip();
        String description = request == null || request.description() == null
                ? ""
                : request.description().strip();
        String visibility = request == null || request.visibility() == null
                ? dev.mosaikit.kernel.core.teams.Team.PRIVATE
                : request.visibility();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            problems.add("name must be 1 to " + MAX_NAME + " characters");
        }
        if (description.length() > MAX_DESCRIPTION) {
            problems.add("description must be at most " + MAX_DESCRIPTION + " characters");
        }
        if (!Set.of(dev.mosaikit.kernel.core.teams.Team.PUBLIC, dev.mosaikit.kernel.core.teams.Team.PRIVATE)
                .contains(visibility)) {
            problems.add("visibility must be public or private");
        }
        if (!problems.isEmpty()) {
            throw new InvalidInputException(problems);
        }
        boolean renamed = current == null || !current.getName().equalsIgnoreCase(name);
        if (renamed && teams.countNamed(org, name) > 0) {
            throw new ConflictException("The organization has a team named " + name + " already.");
        }
        return new TeamRequest(name, description, visibility);
    }

    private UUID me() {
        return RequestOrganization.account(identity)
                .or(() -> directory.idOf(identity.getPrincipal().getName()))
                .orElseThrow(() -> new ForbiddenOperationException("Sign in to use teams."));
    }

    private String actor() {
        return identity.getPrincipal().getName();
    }

    private void audited(String action, dev.mosaikit.kernel.core.teams.Team team, Map<String, ?> detail) {
        audit.append(
                actor(),
                Optional.of(team.getOrganizationId()),
                action,
                team.getId().toString(),
                AuditEvent.Outcome.SUCCEEDED,
                detail);
    }

    private static TeamView view(dev.mosaikit.kernel.core.teams.Team team, String role, List<Member> members) {
        return new TeamView(team.getId(), team.getName(), team.getDescription(), team.getVisibility(), role, members);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
