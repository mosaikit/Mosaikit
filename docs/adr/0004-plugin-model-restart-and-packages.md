# 0004. Plugin model: manifest, restart for backend plugins, signed packages

- Status: accepted
- Date: 2026-09-29
- Deciders: Massimo Antonini

## Context and problem statement

Plugins extend frontend, backend and database, and can extend other plugins. Hot installation
of backend code is not required: restarting the instance is acceptable.

## Decision

- A plugin declares itself in `manifest.yaml`, validated by `PluginManifests` and by the JSON
  Schema shipped with `kernel-api`. Everything not declared is denied.
- Plugins are read from the plugin directory at start. Frontend-only changes take effect at the
  next start today and will be applied without restart later.
- Java plugins will be installed like Keycloak providers: the JAR is added and the Quarkus build
  step runs again at the next start. This is the first spike of the prototype.
- The distribution unit will be a signed `.mkp` archive (manifest, JAR, ES modules, migrations,
  translations, SBOM), stored as an OCI artifact.
- Plugins require other plugins by id and version range; the catalog resolves requirements to a
  fixed point, so a missing requirement disables every dependent plugin.

## Implementation of the Java plugin installation (MK-011)

The spike confirmed the approach and fixed the details:

- **Mutable JAR with a providers directory.** The kernel is packaged with
  `quarkus.package.jar.type=mutable-jar` and `user-providers-directory=providers`. Quarkus
  re-augmentation (`-Dquarkus.launch.rebuild=true`) adds the JARs of that directory as application
  archives, so their entities, beans and REST resources are processed like the kernel's own. It
  looks for them in `<parent>/<output-directory>/providers`, hence
  `quarkus.package.output-directory=kernel`, the name of the directory in an installation.
- **Declared code only.** A plugin declares its JAR in `backend.jar`. The launcher
  (`bin/mosaikit`) reads the plugins with the same `PluginCatalog` as the kernel, copies the JARs
  of the plugins that can run into `providers/` as `<plugin id>.jar`, rebuilds the kernel only
  when the set of SHA-256 digests changed, then records it in `providers/mosaikit-providers.txt`.
- **The kernel trusts only that record.** A plugin whose exact JAR is not recorded is
  `RESTART_REQUIRED` and none of its code, migrations or assets are used; plugins that require it
  become `INCOMPATIBLE`. In development mode, without the launcher, Java plugins are
  `RESTART_REQUIRED`.
- **Migrations at start, per plugin.** The kernel migrates the schema `p_<name>` of every active
  plugin with Flyway, with a history table in that schema, before accepting requests. A failed
  migration stops the start.
- **The rebuild must not need the sources.** Quinoa builds the UI from its sources and would do so
  again at every rebuild, so it is used only in development mode; everywhere else the kernel
  serves the built UI from the `ui/` directory of the installation (`mosaikit.ui.directory`),
  with single-page routing.
- **Containers.** Java plugins are added by building a derived image (`RUN bin/mosaikit build`),
  as Keycloak recommends for its providers, so that pods keep a read-only root filesystem. An
  image that does not start is rolled back by deploying the previous image.
- **Automatic rollback.** Before a rebuild the launcher saves the current build (`kernel/quarkus`,
  `quarkus-run.jar`, `providers`). If the rebuild fails, it restores it at once. Otherwise the new
  JARs stay in `mosaikit-pending.txt` until the kernel has read its plugins and migrated their
  schemas, when it removes the file; if the file is still there at the next start, the kernel did
  not start with them and the launcher restores the previous build without rebuilding.
  `bin/mosaikit` does this at once, restarting the kernel a single time. Rolled back JARs are
  recorded in `mosaikit-rejected.txt`: their plugin is `INVALID` until a different JAR is
  installed.
- **Plugin APIs only for active plugins.** A plugin declares the name of its API
  (`backend.api`); the kernel serves `/api/v1/p/<api>/` only while the plugin is active, because
  the code of a disabled or rolled back plugin can still be in the build until the next rebuild.
  Other paths under `/api/v1/p/` answer 404.

Measured on the sample plugin (a REST resource, an entity and a migration): rebuild 6–7 s,
installed and ready in 11–12 s, restart without changes 4 s, kernel memory at rest 227 MB. A
plugin whose migration fails is rolled back and the kernel is ready again in 15–16 s, without
intervention.

Not covered yet: a plugin that starts but fails a health check later (plugins cannot contribute
health checks yet), and the rollback of several plugins added at once, which are all rejected
because the launcher cannot tell which one broke the kernel.

## Consequences

- No custom class loaders or OSGi.
- Installing, updating or removing a Java plugin costs one rebuild of a few seconds at the next
  start; other restarts cost nothing more. Plugins without Java code never need a rebuild.
- A Java plugin can use only the Quarkus extensions that the kernel already contains.
- The kernel version used for compatibility ignores pre-release and build metadata, so
  snapshots behave like the release they precede.
