# 0024. Hosting on GitHub: one organization, the core and the plugins in separate repositories

- Status: accepted
- Date: 2026-10-01
- Deciders: Massimo Antonini
- Refines [ADR-0012](0012-repository-and-distribution-layout.md) (repository) and
  [ADR-0021](0021-minimal-marketplace.md) (catalogs)

## Context and problem statement

The project was hosted in a private GitLab project, with the plugin API and the SDK published to
the registries of the project, which need a token even to read. Plugin authors could neither find
the project nor depend on its API without asking for access, and plugins developed next to the
kernel would follow its release cycle instead of their own.

## Decision

- **Hosting.** The project lives in the public GitHub organization `mosaikit`; CI and releases run
  on GitHub Actions (`.github/workflows/ci.yml`).
- **Publication.** The container image and the Helm chart go to the GitHub Container Registry,
  `mosaikit-kernel-api` and its parent POM to Maven Central (namespace
  `io.github.mosaikit`, ADR-0025), `@mosaikit/sdk` and `@mosaikit/create-plugin` to npmjs.org,
  the portable archives, the documents, the SBOM and the signed checksums to the GitHub release.
  Everything can be read without a token.
- **Repositories.**
  - `mosaikit`: the kernel, the plugin API and SDKs, the plugin generator, the distributions, the
    documentation, and the sample plugins, which stay here because the integration tests of the
    kernel use them as the contract of the API.
  - `.github`: the profile and the community files of the organization, and the reusable
    workflows that build, test, sign and release a plugin.
  - one repository per plugin, named after its kind (`app-<name>`, ADR-0025), created with
    `@mosaikit/create-plugin`, which depends only on the published API and SDK, calls the
    reusable workflows and releases on its own cycle.
  - `mosaikit.github.io`: the site of the project, and the signed catalog of the plugins
    published by the project (ADR-0021), built from their releases and served by GitHub Pages at
    `/catalog/` (ADR-0025).

## Consequences

- A plugin of the project is built exactly like a third-party plugin: if it needs something that is
  not in the published API, the API is incomplete.
- A breaking change of the API is caught by the plugins, which test against the published kernel
  images; the version range in their manifests (`platform`) says which kernels they support.
- The registries of the GitLab project are no longer updated.
