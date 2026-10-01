# Mosaikit

Mosaikit is a domain-agnostic, plugin-based platform for building multi-organization web
applications. A small kernel provides what every product needs — identity, organizations,
users, localization, theming and plugin management — and everything else is a plugin.
Plugins can extend the user interface, the backend API and the database schema, and they
can in turn be extended by other plugins.

> Status: **early development** (0.1.0-SNAPSHOT). APIs and the plugin contract are not stable yet.

## Principles

- **Small kernel, everything else is a plugin.** The kernel knows nothing about any business
  domain.
- **One contract for everyone.** First-party plugins follow the same rules as third-party ones.
- **Plugins talk through the kernel.** Events, typed services and shared context, all declared
  in the plugin manifest.
- **Restart instead of hot reload for backend plugins.** Installing a Java plugin re-runs the
  Quarkus build step at the next start, the same model Keycloak uses for its providers.
- **Framework-agnostic frontend.** A plugin UI is a Web Component loaded as an ES module; it can
  be written with any framework.

## Repository layout

| Path | Content |
|---|---|
| `kernel/` | The kernel: one Quarkus application with the services, the launcher that loads Java plugins, and the web UI in `src/main/webui` (Lit + Vite). |
| `sdk/` | What third parties need to write plugins: `sdk/java` (the Java plugin API and the manifest schema) and `sdk/js` (`@mosaikit/sdk` for plugin frontends). See [sdk/README.md](sdk/README.md). |
| `plugins/` | Sample plugins (`sample-hello` frontend only, `sample-notes` with Java code). They follow the same rules as third-party plugins. |
| `distributions/` | How Mosaikit is delivered: installation, portable archives, Docker, Kubernetes, Keycloak realm, release scripts. |
| `docs/` | Guides for users and developers, compliance (AgID, ACN, GDPR), decisions (ADR) and the requirements (`docs/requirements`, `MK-xxx`). See [docs/README.md](docs/README.md). |

## Requirements

- JDK 25 (LTS); Maven comes with the wrapper (`./mvnw`, `mvnw.cmd` on Windows)
- Node.js 24 LTS or 26, used by the build for the frontend
- Docker or Podman for the integration tests and the development mode (PostgreSQL and
  Keycloak are started in containers)
- Only for the portable archives (`-Pportable`): Bash with `curl` and `tar`. On Linux and macOS
  they are part of the system; on Windows install [Git for Windows](https://git-scm.com/download/win),
  whose Git Bash the build uses from `C:\Program Files\Git\bin\bash.exe` (another location:
  `-Dportable.bash=<path to bash.exe>`). The Bash of WSL is not used.

## Build

```bash
./mvnw install        # frontend and backend: checks, tests, packages in target/dist
./mvnw install -DskipTests                # faster, without tests
./mvnw install -DskipTests -Pportable     # also the portable archives (Bash: see Requirements)
./mvnw install -DskipTests -Pportable -rf :mosaikit-distributions   # only the distributions again
```

On Windows the commands are the same with `mvnw.cmd` in place of `./mvnw`, in PowerShell
(`.\mvnw.cmd install ...`) or in the Command Prompt (`mvnw.cmd install ...`). `-rf` (resume from)
rebuilds only the last module and needs a previous full `install`.

The result is in `target/dist`:

| Path | What it is |
|---|---|
| `mosaikit/` | the installation for a server or a PC with Java 25 and PostgreSQL 18 |
| `mosaikit-portable/` | the portable archives, `mosaikit-portable-<version>-<platform>.zip` or `.tar.gz`: Java and PostgreSQL included, nothing to install, for Windows, Linux and macOS (`-Pportable`) |
| `mosaikit-docker/` | Docker Compose with PostgreSQL, Keycloak and Mosaikit |
| `mosaikit-k8s/` | the Helm chart |
| `plugins/` | the sample plugins, as packages to copy into `plugins/` |

Each one has a `README.md` that says how to start it.

## Development mode

```bash
./mvnw install -DskipTests       # once
./mvnw -pl kernel quarkus:dev    # live reload of Java code and of the UI
```

The kernel starts on <http://localhost:8080>, creates a bootstrap administrator (`admin` /
`admin-dev-only`) and loads the sample plugins from `plugins/`. See
[docs/developer/development.md](docs/developer/development.md).

## Documentation

- [User and administrator guide](docs/user/README.md): installation, administration, plugins,
  configuration, operations.
- [Developer guide](docs/developer/README.md): architecture, building, plugin development, API.
- [Compliance](docs/compliance/README.md): AgID and ACN QC2 matrix, evidence, open
  non-conformities.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). Commits follow Conventional Commits and carry a
Developer Certificate of Origin sign-off. Security issues: see [SECURITY.md](SECURITY.md).

## License

Copyright © 2026 Massimo Antonini.

Mosaikit is licensed under the [Mozilla Public License 2.0](LICENSE). MPL-2.0 is a file-level
copyleft: modified Mosaikit files must be published under the same license, while plugins
that live in their own files and use the public API may be distributed under any license,
including commercial ones. See [ADR-0002](docs/adr/0002-license-mpl-2.0.md).
