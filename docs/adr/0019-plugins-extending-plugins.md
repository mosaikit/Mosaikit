# 0019. Plugins that extend other plugins

- Status: accepted
- Date: 2026-09-30
- Deciders: Massimo Antonini
- Extends [ADR-0004](0004-plugin-model-restart-and-packages.md) and
  [ADR-0018](0018-row-level-security.md); prototype spikes S2 and S3

## Context and problem statement

Most products built on Mosaikit are an app and the plugins that extend it (a Maps app and its
engines, a Catalog and its connectors). Extensions of the same schema across plugins lead to
cross foreign keys and to updates that cannot be ordered; extensions of a frontend by patching its
code break at every release. The platform needs one way to extend a plugin on each level, without
depending on its internals.

## Decision

- **Data.** A plugin never references the tables of another. It publishes versioned views as its
  data contract (`activity_v1`), created `with (security_invoker = true)` so that the row-level
  security of the caller applies. An extension keeps its field in a table of its own, keyed by the
  identifiers of the other plugin, without a foreign key, and reads the other plugin through the
  views. Joins are made in code or on the views.
- **Order.** The kernel migrates the plugins after those they `require`, so that a migration may
  build on the views of a required plugin; a plugin whose requirement is missing is not active.
- **Interface.** A frontend declares its extension points (`frontend.points`, such as
  `activities.detail`). Other plugins contribute to them in `contributes`, like to the points of
  the kernel; `context.contributionsTo(point)` gives the owner of a point, and only it, the
  contributions of every active plugin. The attributes of a point are its contract; for widgets,
  `element` names the custom element, which the owner creates with its own attributes.
- **Behaviour.** Plugins talk through the event bus, with topics under their own name
  (`activities.completed`, `estimates.changed`), declared in `frontend.bridge` for isolated
  frontends.

The Activities app and its Estimates extension show the three levels
(`JavaPluginInstallationIT.extendsAnAppWithAnotherPluginWithoutForeignKeys`).

## Consequences

- A plugin can be updated without its extensions as long as its views, points and topics keep their
  versioned contract; a breaking change publishes `_v2` next to `_v1`.
- An extension of a missing or older app is refused at start by the requirement check, not at the
  first query.
- Widgets of other plugins are not available to a frontend that runs isolated in an iframe, where
  their elements are not loaded; the point then receives no contributions.
- Extensions cannot delete the data of an activity when the activity is deleted: that needs an
  event from the owner (`activities.deleted`), to add when the samples need it.

## Alternatives considered

- **Foreign keys to the tables of the other plugin.** Simple, but they tie the migrations of both
  plugins and forbid the other plugin to change its tables.
- **Extending the entities of another plugin in Java.** It couples the class loaders and breaks with
  every refactoring of the other plugin.
- **A shared "fields" table with a JSON column.** Flexible but untyped, without constraints and
  without row-level security per extension.
