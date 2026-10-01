# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- The project moved to the public GitHub organization `mosaikit` (ADR-0024): CI and releases run
  on GitHub Actions, the plugin API is published to Maven Central and the SDK to npmjs.org, the
  container image and the Helm chart to the GitHub Container Registry; no token is needed to use
  them.
- `@mosaikit/create-plugin` also writes `.github/workflows/ci.yml`, which builds, checks and
  releases the plugin with the reusable workflow of the organization.
- One build for everything (ADR-0012): `./mvnw install` also builds and checks the frontend and
  writes every deliverable to `target/dist` (installation, Docker, Kubernetes, plugin packages,
  and with `-Pportable` the portable archives).
- Repository: `sdk/java` (plugin API, was `kernel/kernel-api`) and `sdk/js` (`@mosaikit/sdk`)
  for third parties; one `kernel` module with services, launcher and UI; requirements in
  `docs/requirements`.
- Installations: at the root only `mosaikit`, `mosaikit.cmd`, `README.md`, `config/`,
  `plugins/` (and `data/` in the portable); the kernel, with the UI and the launcher inside, and
  the portable Java and PostgreSQL are in `bin/`.
- Plugins are installed as one zip copied into `plugins/`, without unpacking it; each plugin
  build produces it.
- Docker Compose distribution (`target/dist/mosaikit-docker`): PostgreSQL 18, Keycloak and
  Mosaikit configured together, with `mosaikit.identity.keycloak-internal-url` for a kernel that
  reaches Keycloak by another name than the browsers.

### Security

- jackson-databind 2.22.3 over the version of the Quarkus BOM, for CVE-2026-91776 and
  CVE-2026-91777.
- The marketplace inspects a package in a directory of the plugins directory, ignored by the
  scan, instead of the temporary directory that other users share (SonarQube Cloud S5443).
- Isolated frontends talk with the shell through a `MessageChannel` handed over in the `ready`
  message, instead of messages posted to the wildcard origin (SonarCloud S2819).

### Added

- Documentation checked at every push and release documents (ADR-0023): `DocumentationTest`
  (API and settings guides follow the code), `docs/test` (links, ADR index, compliance evidence),
  the `changelog` job on merge requests; `docs/build/build.py` and the `docs` job generate the
  guides in Word and PDF, the compliance workbook (Excel), the release overview (PowerPoint) and
  extended release notes; the `sign` job signs the image and the checksums with cosign keyless.
- Assistant on the tools of the plugins (MK-024, ADR-0022): `POST /api/v1/ai/assistant/replies`
  answers in natural language with any OpenAI-compatible model with tool calling
  (`mosaikit.assistant.*`, for example a local Ollama); changes become drafts; the home page of
  the shell has an assistant panel.
- `create-mosaikit-plugin` (MK-023, `sdk/create-plugin`): creates a plugin that the kernel
  accepts, frontend only or with a Java backend, a schema under row-level security and actions for
  assistants, with a standalone Maven build.
- Minimal marketplace (MK-022, ADR-0021): catalogs are directories with a signed `index.json`
  (`PackageSigningTool index`), local or over HTTPS (`mosaikit.marketplace.sources`); platform
  administrators install, update or upload signed packages from the Plugins page; the build
  publishes `target/dist/plugins` as a signed catalog.
- Plugin frontends written with any framework (MK-021, ADR-0020): the samples Palette (React) and
  Swatch (Vue) share the page, the theme and the event bus, bundled with Vite into one module each;
  plugin packages include `dist/`.
- Plugins that extend other plugins (MK-020, ADR-0019): frontends declare extension points
  (`frontend.points`) and read the contributions of other plugins with
  `context.contributionsTo(point)`; plugins are migrated after those they require. The samples
  Activities (app with a data contract view, an extension point and actions for assistants) and
  Estimates (a field, a widget and an event, without foreign keys) show it.
- Row-level security of the data of organizations (MK-019, ADR-0018): requests run as the member
  role of the installation with the organization of the request, so that the PostgreSQL policies
  of plugin tables apply to every query; `mk_kernel.current_organization()` for the policies;
  `mosaikit.database.row-security` to turn it off. The sample notes plugin uses it.
- AI tool registry (MK-015, ADR-0017): plugins declare typed `actions` with a JSON Schema and a
  risk level; the kernel offers them as tools at `/api/v1/ai/tools` and through an MCP server at
  `/mcp`, with the credentials and the organization of the person. `write` and `execute` tools
  become drafts that the person confirms or rejects under "Pending actions" in the shell. The
  sample notes plugin declares `list-notes` and `create-note`.
- Audit log: append-only `audit_event` table, the `mosaikit.audit` log category and
  `GET /api/v1/audit-events` for platform administrators; it records the actions of assistants and
  the changes of organizations and members.
- Membership of several organizations (MK-017, ADR-0016): one account per person, with roles in
  each organization; each request acts on one organization (realm, sub-domain or the selector of
  the shell); each organization chooses `password`, `realm` or `password-or-realm`; plugins read
  `CurrentOrganization` and plugin APIs are refused without an organization; members are managed
  with `/api/v1/organizations/{slug}/members`.
- Isolated plugin frontends (MK-014, ADR-0015): a frontend that asks for it, or whose publisher
  is not verified (`mosaikit.plugins.unverified-frontends=iframe`, the default), runs in a
  sandboxed iframe and reaches the shell only through the events and the API calls declared in
  `frontend.bridge` of its manifest.
- Signed plugin packages (MK-013, ADR-0014): Ed25519 signature inside the zip, checked by the
  kernel and the launcher against `config/trusted-keys`; a changed package is always refused, and
  `mosaikit.plugins.signatures=required` accepts only packages of trusted publishers.
  `PackageSigningTool` in the Java plugin API generates keys, signs and verifies packages; the
  build signs the sample plugins and trusts their key.

- Code analysis on SonarQube Cloud (Free plan), Java and TypeScript with their coverage, from the
  `sonarqube` job of the pipeline.

- Creation of the Keycloak realm of an organization (MK-018): `POST /api/v1/organizations` with
  `federation` creates the realm with the kernel UI client, the `organization-admin` role and a
  first manager with a temporary password, through a service account of the master realm
  (`mosaikit.identity.admin`); nothing is created when Keycloak refuses or cannot be reached.

- Repository structure, governance files and MPL-2.0 license.
- Kernel: semantic versions and version ranges, plugin manifest model and validation,
  plugin registry loaded from a directory, organizations, local accounts with
  self-registration, system information, RFC 9457 error responses.
- Web shell with plugin contributions loaded from the kernel (`kernel/kernel-ui`).
- `@mosaikit/sdk` with the plugin frontend contract.
- Sample frontend plugin.
- Java plugins installed with a restart (MK-011): `backend.jar` in the manifest, launcher
  `bin/mosaikit` that rebuilds the kernel (mutable JAR) when plugin JARs change, status
  `RESTART_REQUIRED`, Flyway migrations of each plugin schema at start, plugin APIs under
  `/api/v1/p/<api>` always authenticated and served only for active plugins, automatic
  rollback of plugins with which the kernel cannot be rebuilt or started, and the sample Java
  plugin `sample-notes`.
- Federated sign-in with one Keycloak realm per organization (MK-012): realm and email domains
  of each organization (`PUT /api/v1/organizations/<slug>/identity`), sign-in options chosen
  from the sub-domain or the email domain (`GET /api/v1/identity/sign-in-options`), bearer
  tokens verified per realm as dynamic OIDC tenants, federated accounts created or linked at the
  first sign-in, kernel roles only, `503` with a problem detail when a realm is unreachable,
  sign-in in two steps in the UI with the authorization code flow and PKCE, and a Keycloak realm
  template in `distributions/keycloak`.
- Portable distribution (MK-016): archives for Windows, Linux and macOS with a Java runtime
  built with jlink and PostgreSQL 18 started by the launcher on localhost, secrets generated at
  first start, backup by copying `data/`; built and tested in the `portable` CI jobs and
  attached to GitHub releases.
- Installation layout shared by all distributions, with `distributions/installation/assemble.sh`.
- The kernel serves the built UI from the `ui/` directory; Quinoa is used in development mode only.
- Requirements as code, architecture decision records, CI pipeline, container image and
  Helm chart.
- Release pipeline: on a `vX.Y.Z` tag, container image, kernel-api, `@mosaikit/sdk` and the
  Helm chart are published (container image and chart to GHCR, plugin API to Maven Central, SDK to npmjs.org) and a GitHub release is created.
