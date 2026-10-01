# 0025. Publishing on GitHub Pages, and the Maven namespace `io.github.mosaikit`

- Status: accepted
- Date: 2026-10-01
- Deciders: Massimo Antonini
- Refines [ADR-0024](0024-hosting-on-github.md)

## Context and problem statement

The project has no domain of its own. Maven Central grants a namespace such as `dev.mosaikit`
only to whoever proves to own the domain `mosaikit.dev`, so the plugin API could not be published
under the coordinates the build used. The documentation, the catalog of the plugins and a page
where people find the plugins need a public address, and registering and hosting a site is one
more thing to pay for and keep running.

## Decision

- **Maven namespace.** The Maven coordinates are `io.github.mosaikit` (the parent POM, the plugin
  API `io.github.mosaikit:mosaikit-kernel-api`, the kernel and the distributions) and
  `io.github.mosaikit.samples` (the sample plugins): Maven Central verifies that namespace with the
  GitHub organization. The Java packages (`dev.mosaikit.*`) and the plugin identifiers
  (`dev.mosaikit.sample.notes`) do not change: Maven Central does not check them, and they are
  part of the API.
- **GitHub Pages.** Everything public is served by GitHub Pages of the organization, from public
  repositories:
  - `mosaikit.github.io` at `https://mosaikit.github.io/`: the site of the project, with the
    marketplace pages that list the plugins of the catalog, and the JSON schemas
    (`/schemas/plugin-manifest/0.1.json`, `/schemas/requirement/0.1.json`, the `$id` of the
    schemas);
  - `mosaikit` at `https://mosaikit.github.io/mosaikit/`: `docs/` of the default branch, rendered
    by Jekyll (`.github/workflows/docs.yml`);
  - `catalog` at `https://mosaikit.github.io/catalog/`: the signed catalog of ADR-0021, a source
    for `mosaikit.marketplace.sources`.
- **Plugin repositories** are named `plugin-<name>` (`plugin-map`), which keeps them apart from the
  other repositories of the organization.

## Consequences

- Nothing to register or host: the addresses follow the organization. A domain can be added later
  with a `CNAME` file in each repository, without changing the structure.
- The documentation on the site is the one of the default branch; the documents of a release
  (Word, PDF, Excel, PowerPoint) stay attached to the GitHub release.
- Pages of the free plan serve only public repositories: what must stay private (the plugins under
  development) is not on the site until it is released into the catalog.

## Alternatives considered

- **Registering `mosaikit.dev`.** It keeps `dev.mosaikit` as the namespace, but adds a domain to
  renew and a DNS to keep, for no gain over the addresses of the organization.
- **Renaming the Java packages to `io.github.mosaikit`.** Not required by Maven Central, and it
  would break every plugin that imports the API.
