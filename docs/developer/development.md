# Development guide

## Prerequisites

| Tool | Version |
|---|---|
| JDK | 25 (LTS) |
| Maven | 3.9.x |
| Node.js | 24 LTS or 26 |
| Container runtime | Docker or Podman, for Quarkus Dev Services (PostgreSQL) and the tests with Keycloak |
| Bash, curl, tar | only for the portable archives; on Windows Git for Windows (Git Bash) |

## Everyday commands

```bash
./mvnw install                          # everything: frontend and backend checks, tests, target/dist
./mvnw install -DskipTests              # without tests (and without the frontend checks)
./mvnw verify -Dskip.npm                # backend only; the kernel then has no UI
npm run check                           # frontend only: lint, type check, tests with coverage
./mvnw spotless:apply                   # format Java sources and add license headers
npm run format                          # format frontend sources
./mvnw -Pmutation verify -pl sdk/java   # mutation testing with PIT
./mvnw cyclonedx:makeAggregateBom       # software bill of materials
```

The Maven build runs npm itself: `npm ci`, then `npm run build` (SDK and UI, before the kernel,
which packages the UI), then `npm run check` in the test phase.

## Development mode

```bash
./mvnw install -DskipTests       # once, to install the plugin API
./mvnw -pl kernel quarkus:dev
```

- Kernel and UI on <http://localhost:8080>, with live reload of Java code and hot module
  replacement of the UI (Quinoa runs Vite on `kernel/src/main/webui`).
- Dev Services start PostgreSQL in a container; Flyway applies the migrations.
- A platform administrator `admin` / `admin-dev-only` is created at first start.
- Plugins are read from the `plugins/` directory of the repository. Plugins with Java code, such
  as `sample-notes`, stay `RESTART_REQUIRED`: their code is loaded only by the launcher (next
  section).
- Swagger UI: <http://localhost:8080/q/swagger-ui>. Dev UI: <http://localhost:8080/q/dev-ui>.

To work on the UI alone against a running kernel: `npm run dev -w kernel/src/main/webui`
(Vite forwards `/api` and `/q` to port 8080).

## Running an installation

`./mvnw install` leaves an installation in `target/dist/mosaikit` (see its `README.md`). To try
the launcher and Java plugins as they run in production:

```bash
./mvnw install -DskipTests
cp target/dist/plugins/sample-notes-*.zip target/dist/mosaikit/plugins/   # a Java plugin, as it is
# edit target/dist/mosaikit/config/application.properties (database, admin password)
target/dist/mosaikit/mosaikit              # mosaikit.cmd on Windows
```

The first start after adding, updating or removing a Java plugin rebuilds the kernel. A plugin can
also be a directory with the same content as its package, which is handy while writing it.

## Building the portable distribution

The portable archives ([ADR-0010](../adr/0010-portable-distribution.md)) are built from
`target/dist/mosaikit`, on Linux, macOS or Windows (Git Bash); the JDK and PostgreSQL downloads
are cached in `target/portable-cache` (it can be deleted: the next build downloads them again) and
the archives go to `target/dist/mosaikit-portable`:

```bash
./mvnw install -DskipTests -Pportable                  # every platform
./mvnw install -DskipTests -Pportable -Dportable.platforms=windows-x64
distributions/portable/build.sh linux-x64              # by hand, after ./mvnw install
./mvnw -pl kernel failsafe:integration-test failsafe:verify -Dit.test=PortableDistributionIT \
  -Dmosaikit.it.portable=$PWD/target/dist/mosaikit-portable/mosaikit-portable-0.1.0-SNAPSHOT-linux-x64.tar.gz
```

`JLINK_JDK` can point to a local JDK of the same version as the pinned Temurin jmods, to skip
its download.

## Federated sign-in

`FederatedIdentityTest` (MK-012) runs against a real Keycloak: by default it starts
`quay.io/keycloak/keycloak` in a container; `-Dit.keycloak.url=http://localhost:8180` uses a
running one instead (admin `admin`/`admin`). Each run creates two realms with random names from
`distributions/keycloak/realm-template.json`.

To try it in development mode:

```bash
docker run -d --name keycloak -p 8180:8080 -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin quay.io/keycloak/keycloak:26.7.4 start-dev
# import the realm template (redirect URI http://localhost:8080/*), add a user, then:
./mvnw -pl kernel quarkus:dev -Dmosaikit.identity.keycloak-url=http://localhost:8180
# curl asks for the password of admin (admin-dev-only in development mode)
curl -u admin -X PUT localhost:8080/api/v1/organizations/<slug>/identity \
  -H 'Content-Type: application/json' -d '{"realm":"<realm>","emailDomains":["example.com"]}'
```

## Tests and traceability

- Java unit tests live next to the code (`sdk/java`, `kernel`); the integration tests with
  `@QuarkusTest` and REST Assured are in `kernel/src/test/java/dev/mosaikit/kernel/app`.
- `JavaPluginInstallationIT` (`kernel`, run by Failsafe in `verify`) installs the package of the
  sample Java plugin in a real installation and checks MK-011 end to end. It starts PostgreSQL with
  Testcontainers, or uses a server given with `-Dmosaikit.it.jdbc-url=…` (plus `-username`,
  `-password`) on which it creates and drops its own database.
- Frontend tests use Vitest; the JSON Schema of the manifest is tested against every manifest
  in the repository.
- Every test class carries the requirement it verifies: `@Tag("MK-003")` in Java,
  the requirement id in the `describe` title or file in TypeScript when relevant.

## End-to-end tests

[`e2e/`](../../e2e/README.md) runs Playwright on a real installation built by `./mvnw install`:
launcher, kernel, shell and plugins, in Chromium, with a fake language model only. Every
requirement has its e2e cases there, one per acceptance criterion; a requirement is done when they
pass in CI (job `e2e`), not only its unit tests.

```bash
./mvnw install -DskipTests && npm run e2e
```

## CI

The workflow (`.github/workflows/ci.yml`) runs on the GitHub-hosted runners, on pull requests, on
`main` and on release tags; for a public repository the runners are free. A newer run of the same
branch or pull request cancels the running one. The portable archives are built only on tags, or
on demand on `main` (Actions → CI → Run workflow).

The code is analysed on SonarQube Cloud (organization `mosaikit`, project `mosaikit_mosaikit`):
the `sonarqube` job runs when the repository secret `SONAR_TOKEN` is defined, with Automatic Analysis
turned off in SonarQube Cloud.

## Quality gate

See [CONTRIBUTING.md](../../CONTRIBUTING.md#quality-gate). The same checks run locally and in CI:
formatting (Spotless, Prettier), static analysis (compiler warnings, ESLint strict type-checked,
SonarQube), architecture rules (ArchUnit), coverage (JaCoCo, V8) and dependency scanning.
