# 0011. Federated identity: one Keycloak realm per organization

- Status: accepted
- Date: 2026-09-29
- Deciders: Massimo Antonini
- Details [ADR-0005](0005-organizations-and-identity.md)

## Context and problem statement

Organizations want their people to sign in with their own identity providers (Microsoft Entra
ID, Google, SAML, LDAP), while local accounts must keep working for the platform operator, the
portable distribution and emergencies (MK-012). The kernel must not depend on Keycloak to start,
and a realm must never grant access to another organization or to the platform.

## Decision

- **One Keycloak realm per organization.** The realm federates the identity providers of the
  organization and holds its `organization-admin` role; `distributions/keycloak/realm-template.json`
  creates it with the `mosaikit` public client (authorization code flow with PKCE only).
- **Choosing the realm.** The organization has a realm name and a set of email domains, each
  domain owned by one organization. Before sign-in the UI asks for the email and calls
  `GET /api/v1/identity/sign-in-options`: the organization is the one of the sub-domain
  (`<slug>.<mosaikit.identity.domain>`) or else of the email domain. The UI then redirects to
  the realm (authorization code with PKCE, `login_hint`), exchanges the code and sends the access
  token as a bearer token. A person without a realm, or who asks for it, signs in with a
  password.
- **Verifying tokens.** Each realm is a dynamic OIDC tenant of `quarkus-oidc`
  (`OrganizationTenantResolver`), created on first use; the default tenant is disabled, so the
  kernel never contacts Keycloak at start and Basic authentication of local accounts is
  unaffected. On the sub-domain of an organization only its realm is accepted; elsewhere the
  unverified `iss` claim selects the realm, and the token is then verified with the keys and the
  issuer of that realm. Only tokens with `azp` equal to the kernel client are accepted.
- **Accounts.** `FederatedIdentityAugmentor` turns the verified token into a kernel identity: the
  account is found by `(organization, sub)`, created at the first sign-in, or linked to a local
  account of the same organization with the same address if the realm verified it. An address
  used by an account of another organization, or by an account already linked, blocks the
  sign-in. Federated accounts have no local password.
- **Roles.** Only kernel roles of the organization are granted: `organization-user`, plus
  `organization-admin` for the realm role of that name. Every other realm role is dropped, so a
  realm can never grant `platform-admin`.
- **Keycloak down.** A token that cannot be verified because the realm is unreachable gets
  `503` with a problem detail and `Retry-After`; the UI offers the password sign-in.
- The identity settings of organizations are cached in memory for `mosaikit.identity.cache-ttl`
  (30 s), invalidated at once on the instance that changes them.

## Consequences

- The kernel UI keeps tokens in memory only; the PKCE verifier and state live in session storage
  across the redirect. Tokens are refreshed before they expire; sign-out also ends the session
  at the realm.
- Realms are created and configured by the operator; provisioning them from the kernel through
  the Keycloak admin API can follow.
- An account belongs to one organization, as in ADR-0005. Membership of several organizations,
  with the active organization chosen per request, is MK-017.
- `mosaikit.identity.keycloak-url` must be the public URL that appears in the `iss` claim of the
  tokens; the Content Security Policy allows the UI to call it.
- Tests run against a real Keycloak (`KeycloakTestResource`: a container, or `-Dit.keycloak.url`).
