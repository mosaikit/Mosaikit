# 0010. Portable distribution with a jlink runtime and bundled PostgreSQL

- Status: accepted
- Date: 2026-09-29
- Deciders: Massimo Antonini

## Context and problem statement

Demonstrations, offline sites and plugin developers need Mosaikit without installing Java,
PostgreSQL or containers (MK-016). The archive must use the same layout and launcher as every
other distribution, so that Java plugins are installed as in MK-011, and it must be buildable
for Windows, Linux and macOS from one Linux CI job.

## Decision

- One archive per platform (`linux-x64`, `windows-x64`, `macos-arm64`, `macos-x64`), built by
  `distributions/portable/build.sh` on top of the installation layout, plus `runtime/`,
  `pgsql/` and `data/`.
- Java runtime: jlink of the host JDK with the jmods of the target platform (cross-linking).
  Temurin publishes the jmods as a separate download from Java 24 on (the JDK images are built
  with JEP 493 and no longer contain them). Host JDK and jmods must have the same version; both are downloaded and
  verified with their SHA-256. Native symbols are stripped only for Linux targets, because
  `--strip-debug` uses the host `objcopy`.
- The runtime has no `jdk.compiler`; re-augmentation of the mutable JAR
  works with it, so plugins are loaded exactly as in MK-011.
- PostgreSQL 18 binaries from the zonky project, taken from the npm packages
  `@embedded-postgres/<platform>` (the same binaries repackaged, pinned by version). The launcher
  (`database start|stop`) creates the cluster in `data/pgsql` at first start, restricts its
  permissions, listens on `127.0.0.1` only (port `54329`, `MOSAIKIT_DATABASE_PORT`), without
  Unix sockets, with small memory settings, and stops it when the kernel exits.
- Secrets (database password, first admin password) are generated at first start into
  `data/secrets.properties`; the admin password is also written to
  `data/initial-admin-password.txt`. The connection settings are written to
  `config/database.properties` at each start and given to the kernel with
  `quarkus.config.locations`.
- The kernel listens on `127.0.0.1:8080`: the portable is a single-user tool, not a server.
- Backup is a copy of `data/` while Mosaikit is stopped.

## Consequences

- One Linux CI job builds all four archives; only the Linux archive is tested automatically
  (`PortableDistributionIT`). Windows and macOS archives are tested by hand before a release
  until runners for those systems are available.
- The zonky binaries follow PostgreSQL minor releases (18.4 at the time of writing); the pinned
  package version is updated with the other dependencies.
- Measured on Linux x64: first start 6.3 s, 360 MB of memory at rest for kernel and database.
- macOS archives are not signed or notarized: Gatekeeper asks for confirmation at first start.
