# 0016. One account, memberships in several organizations

- Status: accepted
- Date: 2026-09-30
- Deciders: Massimo Antonini
- Extends [ADR-0005](0005-organizations-and-identity.md) and
  [ADR-0011](0011-federated-identity-per-organization.md)

## Context and problem statement

Until now an account belonged to one organization, and a federated sign-in whose email address
was used by an account of another organization was refused (MK-012). People who work for two
administrations, or for an administration and a supplier, needed two accounts. Each organization
must also decide how its people sign in: some require their own identity provider for their data,
while the same person may sign in with a password elsewhere (MK-017).

## Decision

- An account (`user_account`) is the person: one per email address, with a local password or
  none, and only the roles of the platform (`platform-admin`). A membership
  (`organization_member`) ties an account to an organization, with the roles in it
  (`organization-admin`, `organization-user`) and, once linked, the subject of the person in the
  realm of that organization. Migration V3 moves the existing data.
- Every request acts on at most one organization, stored in the security identity:
  - signed in through a realm: the organization of that realm;
  - signed in with a password: the organization named by the `X-Mosaikit-Organization` header
    (the selector of the shell) or by the sub-domain, or else the only organization of the person
    that accepts passwords;

  always one of which the person is a member. The roles of the request are those of the
  membership; the roles of the platform are granted only with a password, so that a realm can
  never grant them.
- Each organization has a sign-in policy: `password`, `realm` or `password-or-realm`. An
  organization with `realm` refuses passwords for its data (the request has no organization),
  while the same person keeps using a password for the organizations that accept it.
- A realm links an existing account only to a membership created for it in its organization, and
  only with a verified email address; a new address gets a new account and membership. A realm can
  therefore never take over an account that does not belong to its organization.
- Plugins read the organization of the request from `CurrentOrganization` of the Java plugin API,
  and the kernel refuses with `403` any request to a plugin API that has no organization, so plugin
  data are always scoped to one organization.
- Platform administrators manage memberships with `/api/v1/organizations/{slug}/members`.

## Consequences

- Plugins must store the organization with their data and filter on it (the sample `notes` plugin
  does, with migration V2); rows written before have no organization and stay hidden.
- A person signed in through a realm works in that organization only; switching to another one
  means signing in to it, which the shell offers through its selector for passwords.
- Managers of an organization cannot manage its members yet: only platform administrators can.
