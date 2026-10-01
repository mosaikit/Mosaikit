# REST API

The kernel API is under `/api/v1`, the APIs of plugins under `/api/v1/p/<api>`, where `<api>` is
the `backend.api` name of the manifest. The OpenAPI 3 document of the running kernel is at
`/q/openapi`; this page gives the conventions that hold for every endpoint.

## Authentication

| Client | How |
|---|---|
| Local accounts (the platform administrator, organizations without a realm) | HTTP Basic, over HTTPS |
| People of an organization with a Keycloak realm | `Authorization: Bearer <access token>` issued by the realm to the `mosaikit` client (authorization code with PKCE) |

The kernel finds the organization of a bearer token from the sub-domain of the request
(`mosaikit.identity.domain`), or from the issuer of the token. `GET /api/v1/identity/sign-in-options?email=`
tells a client how a person signs in.

Every plugin API requires an authenticated person (`quarkus.http.auth.permission.plugin-api`),
and only APIs of active plugins answer.

Every request acts on at most one organization of the person (MK-017): the one of the realm of a
bearer token; with a password, the one named by the `X-Mosaikit-Organization` header or by the
sub-domain, or the only organization of the person that accepts passwords. Requests to plugin
APIs without an organization answer `403`. `GET /api/v1/accounts/me` gives the organization of the
request and every membership of the person.

## Endpoints of the kernel

| Method and path | Who | Purpose |
|---|---|---|
| `GET /api/v1/system/info` | anyone | product, version, active plugins |
| `GET /api/v1/identity/sign-in-options` | anyone | how a person signs in |
| `POST /api/v1/accounts/registrations` | anyone, when the organization allows it | self-registration |
| `GET /api/v1/accounts/me` | signed in | the current account |
| `GET /api/v1/shell/plugins` | signed in | frontends of the active plugins, for the shell |
| `GET /api/v1/plugin-assets/{id}/{path}` | anyone | web files of active plugins |
| `GET /api/v1/plugins` | `platform-admin` | every plugin found, with status, problems and publisher |
| `GET, POST /api/v1/organizations` | `platform-admin` | list and create organizations (with `federation`, their realm) |
| `GET /api/v1/organizations/{slug}` | `platform-admin` | one organization |
| `PUT /api/v1/organizations/{slug}/identity` | `platform-admin` | realm, email domains and sign-in policy of an organization |
| `GET /api/v1/organizations/{slug}/members` | `platform-admin` | the members of an organization |
| `PUT, DELETE /api/v1/organizations/{slug}/members/{email}` | `platform-admin` | add a person, change their roles, remove them |
| `GET /api/v1/marketplace` | `platform-admin` | the catalogs and what they offer, compared with what is installed (MK-022) |
| `POST /api/v1/marketplace/installations` | `platform-admin` | install a package of a catalog (`source`, `id`, `version`); 202, used at the next start |
| `POST /api/v1/plugins/packages` | `platform-admin` | install an uploaded package (`application/zip`); 202, used at the next start |
| `GET /api/v1/audit-events` | `platform-admin` | the latest audit events (`limit`, `organization`) |
| `GET /api/v1/ai/tools` | signed in, with an organization | the tools of the active plugins (MK-015) |
| `POST /api/v1/ai/tools/{tool}/invocations` | signed in, with an organization | invoke a tool: 200 with the result, or 202 with a draft |
| `GET /api/v1/ai/drafts`, `GET /api/v1/ai/drafts/{id}` | the person who proposed them | open drafts, one draft |
| `POST /api/v1/ai/drafts/{id}/confirmation` | the person who proposed it | run a draft |
| `DELETE /api/v1/ai/drafts/{id}` | the person who proposed it | reject a draft |
| `GET /api/v1/ai/assistant` | signed in | whether an assistant is configured, and its model (MK-024) |
| `POST /api/v1/ai/assistant/replies` | signed in, with an organization | ask the assistant: `{"messages": [{"role": "user", "content": "…"}]}`; returns the reply, the tools used and the drafts |
| `GET /mcp` | signed in | answers 405: the MCP server opens no event stream of its own |
| `POST /mcp` | signed in, with an organization | MCP server with the same tools |

## Tools for assistants and MCP

Each action of an active plugin is a tool named `<api>__<action>`, for example
`sample-notes__create-note`. The body of an invocation is the arguments, checked against the input
schema of the action (400 with one error per field otherwise):

```http
POST /api/v1/ai/tools/sample-notes__create-note/invocations
Authorization: Basic ...
X-Mosaikit-Organization: acme
Content-Type: application/json

{"text": "Call the supplier"}
```

A `read` tool answers `{"outcome": "executed", "result": {"status": 200, "body": ...}}`. A
`write` or `execute` tool answers 202 with `{"outcome": "drafted", "draft": {...}}`: nothing
changes until the same person confirms the draft within 15 minutes. The kernel calls the plugin
with the credentials of the request, so the plugin checks the permissions of the person.

MCP clients connect to `https://<host>/mcp` with the same credentials and the
`X-Mosaikit-Organization` header, and use `initialize`, `tools/list` and `tools/call`. The server
answers JSON (no event stream), refuses browser requests from another origin and returns drafts
for the tools that change data.

## Errors

Errors are problem details (RFC 9457, `application/problem+json`), without stack traces or
internal messages:

```json
{
  "type": "about:blank",
  "title": "Invalid request",
  "status": 400,
  "detail": "Correct the listed fields and retry.",
  "errors": [{ "field": "slug", "message": "use lowercase letters, digits and inner hyphens" }]
}
```

| Status | When |
|---|---|
| `400` | validation failed; `errors` lists the fields |
| `401`, `403` | not signed in, or not allowed |
| `404` | not found, or an API of a plugin that is not active |
| `409` | conflict, for example a slug, an email domain or a realm already in use |
| `503` | a service the request needs (Keycloak) cannot be reached |

## Versioning

`/api/v1` changes only by addition: new endpoints, new optional fields. Clients ignore fields
they do not know. A breaking change becomes `/api/v2`, with both versions served for a release
cycle.
