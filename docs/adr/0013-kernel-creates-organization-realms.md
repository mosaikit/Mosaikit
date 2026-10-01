# 0013. The kernel creates the realm of a federated organization

- Status: accepted
- Date: 2026-09-30
- Deciders: Massimo Antonini
- Extends [ADR-0011](0011-federated-identity-per-organization.md)

## Context and problem statement

With ADR-0011 every federated organization needed manual work in the Keycloak console (import the
template, create users, assign roles) and then a second call to Mosaikit. That is slow and easy to
get wrong, and it keeps the realm and the organization out of step (MK-018).

## Decision

- `POST /api/v1/organizations` accepts `federation` (email domains and, optionally, a first
  manager). The kernel then creates the realm, named after the slug, from the template packaged in
  the kernel (`realm-template.json`): the `mosaikit` public client with PKCE and the redirect
  addresses of the UI (the address of the request and the sub-domain of the organization), the
  `organization-admin` role, and the manager with a temporary password and the
  `UPDATE_PASSWORD` required action. The password is returned once and never stored.
- The kernel authenticates to Keycloak with the client credentials of a confidential client of
  the `master` realm with the `admin` role (`mosaikit.identity.admin`), at the internal URL of
  Keycloak when one is set. Docker Compose creates that client with
  `KC_BOOTSTRAP_ADMIN_CLIENT_ID` and `KC_BOOTSTRAP_ADMIN_CLIENT_SECRET`.
- Organization and realm are created together: the organization is saved in the same
  transaction, which rolls back when Keycloak refuses (`409` for an existing realm) or cannot be
  reached (`503`); a realm created for an organization that then cannot be saved is deleted.
- Realms created by hand keep working (`PUT /api/v1/organizations/<slug>/identity`).

## Consequences

- The service account can administer every realm of Keycloak: its secret is a production secret
  (Kubernetes secret, `.env` out of version control). A narrower role (`create-realm` plus
  management of the created realms) can replace `admin` later.
- Deleting an organization does not delete its realm yet; that comes with the life cycle of
  organizations.
