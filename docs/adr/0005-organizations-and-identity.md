# 0005. Organizations and identity

- Status: accepted
- Date: 2026-09-29
- Deciders: Massimo Antonini

## Context and problem statement

The platform serves several organizations on one installation (SaaS) or a single one
(portable, on-premises). People must be able to sign in without an external identity provider
(portable distribution, emergency access) and, where available, through federation.

## Decision

- The unit of isolation is the **organization** (the term "tenant" is not used).
- Kernel roles: `platform-admin` (operator of the installation), `organization-admin`
  (manager of an organization) and `organization-user`.
- Local accounts are part of the kernel. Passwords are hashed with PBKDF2-HMAC-SHA256 from the
  JDK, 600,000 iterations, encoded with algorithm and cost so that the cost can grow.
- Federation uses Keycloak with one realm per organization, in a later iteration.
- The first platform administrator is created at first start from configuration; there is no
  default password outside development.

## Consequences

- Authentication works with HTTP Basic in this iteration; session-based sign-in and OIDC
  follow.
- An account belongs to one organization for now; memberships of several organizations follow.
