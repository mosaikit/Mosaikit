# 0032. Fluent UI web components behind `@mosaikit/ui`, with selectable themes

- Status: accepted
- Date: 2026-10-02
- Deciders: Massimo Antonini
- Refines [ADR-0020](0020-frontends-in-any-framework.md) (frontends in any framework)

## Context and problem statement

The shell is going to look and work like a collaboration suite (Teams-like workspace), and plugins
written in any framework must look like part of it. Public administrations, a large share of the
installations, expect the look of the AgID design guidelines (Bootstrap Italia) and must meet the
accessibility obligations of WCAG 2.1 AA. A component library has to work across the Shadow DOM
of plugins and in every framework, be open source and themeable.

## Considered options

- Fluent UI web components (Microsoft, MIT): the look of a collaboration suite, design tokens;
  no data grid, date picker or toast yet.
- SAP UI5 Web Components or Carbon Web Components (Apache 2.0): richer, with data tables and date
  pickers, but with a strong look of their own.
- Bootstrap or Bootstrap Italia: global CSS, which does not reach into the Shadow DOM of plugins.

## Decision

- **Fluent UI web components for the whole interface**, the shell and its public pages, through
  `@mosaikit/ui`: it defines the `fluent-*` elements once for the page and applies the theme.
  Missing components (data grid, date picker, toast) are added to `@mosaikit/ui`.
- **Themes as tokens.** A theme gives the tokens of Fluent UI and the `--mk-*` tokens that plugins
  read, for a light and a dark scheme. The installation chooses it with `mosaikit.ui.theme`:
  `mosaikit` (default) or `pa`, in the style of Bootstrap Italia (#0066CC, Titillium Web, visible
  focus). Colors that would miss WCAG 2.1 AA contrast are darkened, and a test checks the contrast
  of every theme.
- **Fonts are served by the kernel** (Titillium Web, SIL OFL 1.1): pages load nothing from other
  sites.
- **Credentials stay native inputs**, styled with the tokens: password managers do not fill inputs
  inside the Shadow DOM of components reliably.

## Consequences

- Plugins use `fluent-*` elements and `--mk-*` tokens; replacing Fluent UI later changes
  `@mosaikit/ui`, not the plugins.
- The `pa` theme gives the look of Bootstrap Italia, not the library: services whose conformity
  criteria require the Bootstrap Italia library itself (for example the websites of municipalities
  of the PNRR measure 1.4.1) need it in their own pages.
- Every page is checked with axe-core for WCAG 2.1 AA in the end-to-end tests.
