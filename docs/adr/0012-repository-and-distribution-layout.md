# 0012. One build, a plugin SDK and compact distributions

- Status: accepted
- Date: 2026-09-30
- Deciders: Massimo Antonini
- Refines [ADR-0004](0004-plugin-model-restart-and-packages.md) (plugin packages) and
  [ADR-0010](0010-portable-distribution.md) (layout of the portable archive)

## Context and problem statement

The prototype had five Maven modules for the kernel, a separate npm build for the UI, a shell
script to assemble an installation, outputs outside the project and installations with many
top-level folders (`kernel`, `launcher`, `ui`, `runtime`, `pgsql`). Installing a plugin meant
copying its source folder. Building, running and explaining Mosaikit was harder than it needs to
be.

## Decision

- **Repository.** `sdk/` holds what third parties receive: `sdk/java` (artifact
  `mosaikit-kernel-api`, packages unchanged) and `sdk/js` (`@mosaikit/sdk`). `kernel/` is one
  Quarkus module with the services, the launcher and the web UI (`kernel/src/main/webui`).
  `plugins/` holds the samples, `distributions/` how Mosaikit is delivered, `docs/` also the
  requirements (`docs/requirements`).
- **One command.** `./mvnw install` runs npm (install, build, checks) from the parent POM, builds
  the plugin API, the sample plugins and the kernel, and writes every deliverable to
  `target/dist`: `mosaikit/` (installation), `mosaikit-docker/`, `mosaikit-k8s/`, `plugins/`,
  and with `-Pportable` the portable archives. `-Dskip.npm` builds the backend alone.
- **The UI is inside the kernel.** npm builds it before the kernel; Maven copies the result into
  the kernel JAR (`mosaikit-ui/`), from where `UserInterfaceRoutes` serves it. Rebuilding the
  kernel for a Java plugin still never needs Node.js. The launcher is part of the kernel JAR too.
- **Installation layout.** At the root only what a person uses: the `mosaikit` and
  `mosaikit.cmd` launchers, `README.md`, `config/`, `plugins/` and, for the portable, `data/`.
  Everything else is in `bin/`: `bin/kernel` (the mutable JAR, which cannot be a single JAR
  because Quarkus rebuilds it for Java plugins), and for the portable `bin/java` and `bin/pgsql`.
- **Plugin packages.** A plugin is delivered as one zip with its content at the root
  (`manifest.yaml`, `lib/`, `db/`, `web/`, no sources), built by each plugin POM with the shared
  descriptor `plugins/plugin-package.xml`. It is installed by copying the zip into `plugins/`
  as it is: `PluginCatalog` unpacks it into a work directory (`bin/kernel/plugin-packages`, set by
  the launcher) again only when the zip changes, refusing entries outside the target and
  oversized packages. Directories with the same content still work, for plugin development.
  Signing the package remains as decided in ADR-0004.

## Consequences

- CI runs the frontend and the backend in separate jobs, in the images that have their tools; the
  backend job builds with `-Dskip.npm` from the UI built by the frontend job.
- The container image is built from `target/dist/mosaikit`; derived images add plugin zips and
  run `mosaikit build`, which unpacks and loads them at image build time.
- Paths in scripts and documents changed once; the Java packages and the published artifacts did
  not, so plugins are not affected.
