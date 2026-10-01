# 0003. Quarkus in JVM mode with Jakarta Data

- Status: accepted
- Date: 2026-09-29
- Deciders: Massimo Antonini

## Context and problem statement

The kernel must start fast and use little memory, support Java plugins added after the build,
and survive the move to Quarkus 4 (expected November 2026: Java 21 baseline, Jackson 3, Vert.x
5, Jakarta Data instead of Panache, a new event API).

## Decision

- Quarkus 3.39 now, 3.40 LTS when released, then Quarkus 4; Java 25 LTS.
- JVM mode with the Java 25 AOT cache. Native image is closed-world and would prevent adding
  Java plugins without rebuilding; it is kept for satellite services and tools.
- Persistence through Jakarta Data repositories generated at build time by the
  `quarkus-data-processor` annotation processor (it wraps the Hibernate processor and its
  version is managed by the Quarkus BOM). No Panache.
- No direct use of the Vert.x event bus; the kernel will expose its own event API.
- `kernel-api` depends on the JDK only (enforced by ArchUnit).

## Consequences

- Migration to Quarkus 4 stays inside `kernel-core` and `kernel-app`; plugins are not affected.
- A nightly job will build the kernel against the Quarkus 4 beta.
- Startup time and memory are measured in the prototype (targets: < 1.5 s, < 250 MB RSS).
