# 0031. Frontend plugins on a data API of the kernel, active without a restart

- Status: accepted
- Date: 2026-10-02
- Deciders: Massimo Antonini
- Refines [ADR-0021](0021-minimal-marketplace.md) (installation) and
  [ADR-0018](0018-row-level-security.md) (data of organizations)

## Context and problem statement

Mosaikit is built by one person with little time. Every plugin with its own data needed a Java
backend, a schema and its migrations, and its installation took effect only at the next start,
after the kernel was built again with the new code. Most of the first apps keep simple records of
an organization (lists, settings, small registers), for which that cost is out of proportion.

## Decision

- **Collections of documents.** A plugin declares in its manifest `data.collections`, a list of
  names (`^[a-z][a-z0-9-]{0,63}$`). The kernel keeps, for each organization, JSON documents of
  those collections in its own table `plugin_document`, under the same row-level security as the
  rest of the data of organizations, and serves them at `/api/v1/data/<plugin id>/<collection>`:
  list, add, get, replace (with `If-Match` on the version, 409 on a conflict), delete. A
  document is a JSON object of at most 256 KiB; a collection holds at most 10,000 documents.
  Only declared collections of active plugins exist; anything else is 404.
- **SDK and bridge.** `context.data('<collection>')` of `@mosaikit/sdk` gives a typed client;
  in an isolated frontend it goes through the bridge, whose service `data` allows calls under the
  collections of the plugin only.
- **Installation without a restart.** A plugin without a backend and without a schema is active
  as soon as it is installed: the marketplace reads the plugin directory again, answers
  `restartRequired: false` and the shell loads the new frontend without a reload of the page.
  Plugins with a backend or a schema still take effect at the next start.
- **Frozen capabilities.** Federation with realms per organization, isolated frames, the MCP
  server, the portable distribution, the Helm chart and the signed marketplace stay as they are:
  they are tested every night, and no new work goes into them until a real installation needs it.

## Consequences

- A new app is a manifest and a JavaScript file; it can be written, installed and used in minutes.
- The data of such apps is not relational: queries beyond listing and paging need a backend.
  When an app outgrows its collections, it gets a backend and a schema, and migrates its
  documents itself.
- The kernel owns one more table and its limits; the limits are constants, to be made settings
  when an installation needs it.
