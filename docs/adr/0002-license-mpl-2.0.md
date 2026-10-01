# 0002. License: Mozilla Public License 2.0

- Status: accepted
- Date: 2026-09-29
- Deciders: Massimo Antonini

## Context and problem statement

Mosaikit must be open source, keep improvements to the platform open, and still allow a
marketplace where third parties sell commercial plugins. AGPL is excluded by the target
contexts (enterprise, defense). A strong copyleft such as GPL would extend to plugins that run
inside the kernel process, which would prevent commercial plugins.

## Considered options

- GPL-3.0-or-later
- GPL-3.0 with a plugin exception
- EUPL-1.2
- **MPL-2.0**
- Apache-2.0

## Decision

Mosaikit is licensed under **MPL-2.0**. The copyleft applies file by file: modified Mosaikit
files must be published under MPL-2.0, while plugins in their own files that use the public API
(`kernel-api`, `@mosaikit/sdk`, the manifest schema) can use any license. MPL-2.0 is compatible
with GPL, and it is an open license accepted for reuse in the Italian public administration.

## Consequences

- Every source file carries `SPDX-License-Identifier: MPL-2.0` and a copyright line; Spotless
  checks Java headers, and `REUSE.toml` covers files without comments.
- Contributions are accepted under the Developer Certificate of Origin (`git commit -s`).
- The boundary between platform and plugin is the public API: plugins must not copy kernel
  files, otherwise those files remain MPL-2.0.
