# 0020. Plugin frontends in any framework, bundled per plugin

- Status: accepted
- Date: 2026-09-30
- Deciders: Massimo Antonini
- Extends [ADR-0006](0006-frontend-web-components.md); prototype spike S2

## Context and problem statement

The shell loads each plugin frontend as an ES module that defines custom elements
(ADR-0006). Teams and third parties will write plugins with React, Vue or Angular, often with
different versions; the proposal considered Piral or a shared import map for the frameworks.

## Decision

- A plugin frontend written with a framework is bundled with it (Vite library mode) into the one
  ES module its manifest declares, such as `dist/index.js`. The framework is private to the
  plugin: two plugins can use different versions without coordinating.
- The contract stays the one of every plugin: custom elements with shadow DOM, the plugin context
  (`events`, `contributionsTo`, `fetch`, `locale`, `user`) and the `--mk-*` theme tokens, which
  cross the shadow boundary. The framework renders inside the element and is unmounted in
  `disconnectedCallback`.
- Plugins are npm workspaces of the repository: the Maven build runs their `build` script before
  packaging, and `plugin-package.xml` packages `dist/`.
- The samples: `sample-react` (an app declaring the `palette.preview` point and publishing
  `palette.selected`) and `sample-vue` (a widget contributed to that point, following the event).

## Consequences

- No shared runtime to version and no import map to maintain; each plugin carries its framework:
  about 78 kB compressed for React, 24 kB for Vue, measured on the samples. A page with many React
  plugins downloads React several times.
- A shared import map for the frameworks remains possible later as an optimisation, without
  changing the contract of the plugins.
- Isolated frontends (ADR-0015) work the same way: the frame imports the same module.

## Alternatives considered

- **Piral.** A complete micro-frontend framework, but it imposes its own application shell and
  pilet format in place of the plugin contract of Mosaikit.
- **A shared import map of the frameworks.** Smaller pages, but every plugin must agree on one
  version of each framework, which breaks the independence of third-party plugins.
