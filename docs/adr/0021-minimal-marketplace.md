# 0021. A minimal marketplace: signed catalogs and installation from the interface

- Status: accepted
- Date: 2026-09-30
- Deciders: Massimo Antonini
- Extends [ADR-0004](0004-plugin-model-restart-and-packages.md) and
  [ADR-0014](0014-signed-plugin-packages.md); prototype spike S6

## Context and problem statement

Installing a plugin means copying a zip into `plugins/` and restarting (ADR-0004). Administrators
of public bodies should not handle files on servers, and many installations have no network: the
packages come on a removable medium. A catalog must say which packages exist, and nobody must be
able to slip a package into it.

## Decision

- **Catalog.** A catalog is a directory of packages with `index.json` (id, version, name, file,
  SHA-256, size and publisher key of each package) and `index.json.sig`, the Ed25519 signature of
  the index by the publisher of the catalog (`PackageSigningTool index`). The build publishes
  `target/dist/plugins` as a catalog signed with the build key.
- **Sources.** `mosaikit.marketplace.sources` lists catalogs: `https:` for a published one,
  `file:` for a local or removable directory. A catalog whose index is not signed by a key of
  `config/trusted-keys` is shown as refused and offers nothing.
- **Installation.** Platform administrators install or update a package of a catalog, or upload a
  package file, from the Plugins page of the shell (`/api/v1/marketplace`,
  `/api/v1/marketplace/installations`, `/api/v1/plugins/packages`). The kernel accepts a package
  only if it matches the signed index (size and SHA-256), is signed by a trusted publisher, and has
  a valid manifest; it places it in `plugins/` as `<id>-<version>.zip`, keeps the package it
  replaces in `plugins/.previous`, records the event in the audit log, and the package takes
  effect at the next start.
- **OCI registries.** Not in this step: a package is already a file that ORAS stores as an OCI
  artifact, and a registry (zot) can serve a catalog directory; signatures stay those of
  Mosaikit, so cosign or Notation are optional additions.

## Consequences

- Installation from the interface needs no access to the server; offline installations use a
  directory on a removable medium, or the upload of a file.
- The kernel still does not change plugins while running: a restart applies the change, and the
  launcher rolls back a plugin that breaks the start.
- The trusted keys now also decide which catalogs are usable: a catalog publisher is a trusted
  publisher.
- Removing a plugin and restoring a replaced package from `.previous` are still file operations.

## Alternatives considered

- **A central marketplace service.** It would decide for every installation which plugins exist;
  public bodies need their own curated catalogs, often offline.
- **Registries only (OCI).** Standard, but they need a server even for an offline transfer, and
  their signatures (cosign, Notation) need extra tools on every installation.
