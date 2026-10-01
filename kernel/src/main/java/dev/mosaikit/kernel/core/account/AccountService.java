// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.core.account;

import dev.mosaikit.kernel.core.error.ConflictException;
import dev.mosaikit.kernel.core.error.ForbiddenOperationException;
import dev.mosaikit.kernel.core.error.ResourceNotFoundException;
import dev.mosaikit.kernel.core.organization.Organization;
import dev.mosaikit.kernel.core.organization.OrganizationMember;
import dev.mosaikit.kernel.core.organization.OrganizationMembers;
import dev.mosaikit.kernel.core.organization.OrganizationService;
import dev.mosaikit.kernel.core.security.PasswordHasher;
import dev.mosaikit.kernel.core.security.Roles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Creates local accounts, resolves the current one and manages memberships (MK-003, MK-017). */
@ApplicationScoped
@Transactional
public class AccountService {

    /** Roles a person can have in an organization. */
    static final Set<String> ORGANIZATION_ROLES = Set.of(Roles.ORGANIZATION_ADMIN, Roles.ORGANIZATION_USER);

    private final UserAccounts accounts;
    private final OrganizationMembers members;
    private final OrganizationService organizations;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public AccountService(
            UserAccounts accounts,
            OrganizationMembers members,
            OrganizationService organizations,
            PasswordHasher passwordHasher,
            Clock clock) {
        this.accounts = accounts;
        this.members = members;
        this.organizations = organizations;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    /** Registers a person in an organization that allows self-registration. */
    public AccountView register(RegistrationRequest request) {
        Organization organization = organizations.require(request.organization());
        if (!organization.isSelfRegistration()) {
            throw new ForbiddenOperationException("Organization '" + organization.getSlug()
                    + "' does not allow self-registration. Ask its manager for an invitation.");
        }
        if (!organization.getSignIn().allowsPassword()) {
            throw new ForbiddenOperationException("Organization '" + organization.getSlug()
                    + "' signs in through its identity provider: sign in there instead.");
        }
        String username = normalize(request.email());
        if (accounts.countByUsername(username) > 0) {
            throw new ConflictException("An account with this email already exists. Sign in or reset the password.");
        }
        var account = new UserAccount(
                username,
                request.displayName().trim(),
                passwordHasher.hash(request.password().toCharArray()),
                Set.of(),
                Instant.now(clock));
        accounts.insert(account);
        members.insert(new OrganizationMember(
                account.getId(), organization.getId(), Set.of(Roles.ORGANIZATION_USER), Instant.now(clock)));
        return view(account, Set.of(Roles.ORGANIZATION_USER), Optional.of(organization.getId()));
    }

    /** Creates the platform administrator when the installation has no account yet. */
    public Optional<AccountView> createFirstAdministrator(String username, char[] password) {
        if (accounts.countAll() > 0) {
            return Optional.empty();
        }
        var account = new UserAccount(
                normalize(username),
                "Platform administrator",
                passwordHasher.hash(password),
                Set.of(Roles.PLATFORM_ADMIN),
                Instant.now(clock));
        accounts.insert(account);
        return Optional.of(view(account, account.getRoles(), Optional.empty()));
    }

    /**
     * Returns the account with the given login name, as seen by the current request.
     *
     * @param roles the roles of the request
     * @param organizationId the organization of the request
     */
    public AccountView get(String username, Set<String> roles, Optional<UUID> organizationId) {
        return accounts.findByUsername(normalize(username))
                .map(account -> view(account, roles, organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("No account for the current identity."));
    }

    /** The members of an organization. */
    public List<MemberView> members(String slug) {
        Organization organization = organizations.require(slug);
        return members.findByOrganizationId(organization.getId()).stream()
                .map(member -> accounts.findById(member.getAccountId()).map(account -> MemberView.of(account, member)))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(MemberView::username))
                .toList();
    }

    /**
     * Adds a person to an organization, or changes their roles in it (MK-017). A person without an
     * account gets one without password: they sign in through the realm of the organization, which
     * links the account at the first sign-in.
     */
    public MemberView putMember(String slug, String email, MemberRequest request) {
        Organization organization = organizations.require(slug);
        Set<String> roles =
                request == null || request.roles() == null || request.roles().isEmpty()
                        ? Set.of(Roles.ORGANIZATION_USER)
                        : Set.copyOf(request.roles());
        if (!ORGANIZATION_ROLES.containsAll(roles)) {
            throw new ForbiddenOperationException(
                    "A member can have only the roles " + new TreeSet<>(ORGANIZATION_ROLES) + ".");
        }
        String username = normalize(email);
        UserAccount account = accounts.findByUsername(username).orElseGet(() -> {
            String name = request == null
                            || request.displayName() == null
                            || request.displayName().isBlank()
                    ? username
                    : request.displayName().trim();
            var created = UserAccount.withoutPassword(username, name, Instant.now(clock));
            accounts.insert(created);
            return created;
        });
        Optional<OrganizationMember> existing =
                members.findByAccountIdAndOrganizationId(account.getId(), organization.getId());
        OrganizationMember member;
        if (existing.isPresent()) {
            member = existing.get();
            member.replaceRoles(roles);
            members.update(member);
        } else {
            member = new OrganizationMember(account.getId(), organization.getId(), roles, Instant.now(clock));
            members.insert(member);
        }
        return MemberView.of(account, member);
    }

    /** Removes a person from an organization; the account stays, with its other memberships. */
    public void removeMember(String slug, String email) {
        Organization organization = organizations.require(slug);
        accounts.findByUsername(normalize(email))
                .flatMap(account -> members.findByAccountIdAndOrganizationId(account.getId(), organization.getId()))
                .ifPresentOrElse(members::delete, () -> {
                    throw new ResourceNotFoundException("No member '" + email + "' in organization '" + slug + "'.");
                });
    }

    /** Looks up an account for authentication, which runs outside of any HTTP request context. */
    @ActivateRequestContext
    Optional<UserAccount> findForAuthentication(String username) {
        return accounts.findByUsername(normalize(username));
    }

    private AccountView view(UserAccount account, Set<String> roles, Optional<UUID> organizationId) {
        Map<UUID, Organization> byId =
                organizations.all().stream().collect(Collectors.toMap(Organization::getId, Function.identity()));
        List<AccountView.MembershipView> memberships = members.findByAccountId(account.getId()).stream()
                .flatMap(member -> Optional.ofNullable(byId.get(member.getOrganizationId())).stream()
                        .map(organization -> new AccountView.MembershipView(
                                organization.getId(),
                                organization.getSlug(),
                                organization.getName(),
                                member.getRoles(),
                                organization.getSignIn())))
                .toList();
        return new AccountView(
                account.getId(),
                account.getUsername(),
                account.getDisplayName(),
                Set.copyOf(roles),
                organizationId.orElse(null),
                organizationId.map(byId::get).map(Organization::getSlug).orElse(null),
                memberships);
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
