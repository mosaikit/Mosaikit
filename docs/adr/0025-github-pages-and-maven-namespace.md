# 0025. Publishing on GitHub Pages, and the Maven namespace `io.github.mosaikit`

- Status: accepted
- Date: 2026-10-01
- Deciders: Massimo Antonini
- Refines [ADR-0024](0024-hosting-on-github.md) and [ADR-0008](0008-requirements-and-documentation-as-code.md)
  (documentation site)

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
- **GitHub Pages.** Everything public is served by GitHub Pages of the organization, from two
  public repositories:
  - `mosaikit.github.io` at `https://mosaikit.github.io/`: the site of the project; at `/catalog/`
    the signed catalog of ADR-0021, built from the releases of the plugins listed in its
    `catalog/catalog.yml` and a source for `mosaikit.marketplace.sources`; at `/plugins/` a
    page that lists the plugins of the catalog; at `/schemas/` the JSON schemas
    (`plugin-manifest/0.1.json`, `requirement/0.1.json`, the `$id` of the schemas);
  - `mosaikit` at `https://mosaikit.github.io/mosaikit/`: `docs/` of the default branch, rendered
    by Jekyll (`.github/workflows/docs.yml`).
- **Marketplace, first phase.** The Plugins page of the kernel (ADR-0021) is the marketplace: it
  reads the catalog on GitHub Pages and installs from it. A marketplace with publishers, admission
  checks and licences is a later step, as a plugin.
- **Plugin repositories** are named after the main kind of the plugin: `app-<name>`,
  `ext-<name>`, `service-<name>`, `theme-<name>`, `locale-<name>`, `auth-<name>` (`app-maps`,
  id `dev.mosaikit.maps`). For the kernel they are all plugins; the kind is in the manifest, the
  name only helps people find them. The apps ported from Geoportal (`app-data`, `app-maps`,
  `app-dashboards`, `app-processes`) are plugins of the project like any other: the kernel stays
  agnostic of their domain.

## Consequences

- Nothing to register or host: the addresses follow the organization. A domain can be added later
  with a `CNAME` file in each repository, without changing the structure.
- The documentation site uses Jekyll, which GitHub Pages runs without a build of ours, instead of
  the Docusaurus of ADR-0008. The documentation on the site is the one of the default branch; the
  documents of a release (Word, PDF, Excel, PowerPoint) stay attached to the GitHub release.
- Pages of the free plan serve only public repositories: what must stay private (the plugins under
  development) is not on the site until it is released into the catalog. A catalog that reads
  private releases needs a token (`CATALOG_TOKEN`).
- Until the key of the catalog exists (`CATALOG_SIGNING_KEY`), the site is published without
  `/catalog/`.

## Alternatives considered

- **Registering `mosaikit.dev`.** It keeps `dev.mosaikit` as the namespace, but adds a domain to
  renew and a DNS to keep, for no gain over the addresses of the organization.
- **Renaming the Java packages to `io.github.mosaikit`.** Not required by Maven Central, and it
  would break every plugin that imports the API.
