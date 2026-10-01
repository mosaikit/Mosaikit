# 0015. Isolated plugin frontends in sandboxed iframes

- Status: accepted
- Date: 2026-09-30
- Deciders: Massimo Antonini
- Extends [ADR-0006](0006-frontend-web-components.md) and [ADR-0014](0014-signed-plugin-packages.md)

## Context and problem statement

Plugin frontends are ES modules loaded into the page of the shell (ADR-0006): they share its DOM,
its memory and the access token of the person. That is right for code of trusted publishers, and
wrong for the others: a malicious or broken module could read the token, call any API as the
person, or change the page. Plugins from unverified publishers must run so that they can use only
what they declared (MK-014), without a second programming model for plugin authors.

## Decision

- The shell runs a frontend in an iframe when its manifest says `isolation: iframe`, or when its
  publisher is not verified (ADR-0014) and `mosaikit.plugins.unverified-frontends` is `iframe`,
  the default. `GET /api/v1/shell/plugins` gives the effective isolation and the bridge.
- The iframe is a `srcdoc` document with `sandbox="allow-scripts allow-forms"`, without
  `allow-same-origin`: it has an opaque origin, so it cannot reach the DOM, the storage or the
  cookies of the shell. It inherits the Content Security Policy of the shell: scripts come from
  the kernel only, it can connect only to the kernel, which rejects it without credentials, and
  `form-action 'none'` keeps forms from sending data elsewhere.
- The frame loads `frame.js`, a second entry of the UI build, which imports the module of the
  plugin (plugin assets and the UI files carry `Access-Control-Allow-Origin: *`, as modules of an
  opaque origin are loaded with CORS) and activates it with a context of the same shape as for
  modules.
- The frame announces itself with a `ready` message posted to the origin of the shell only,
  carrying one end of a `MessageChannel`; the shell accepts it only from the window of its own
  frame. From then on both sides talk only through that channel (`publish`, `subscribe`, `call`,
  `event`, `result`), so no message is ever posted to a wildcard origin. The manifest declares the bridge:
  `publishes`, `subscribes` (topics or `.*` prefixes) and `services`. The only service is `api`:
  the shell calls the backend API of the plugin with the credentials of the person, which the
  frame never sees, and refuses any other path.

## Consequences

- The same module runs as a module or isolated; authors declare the bridge once.
- Isolated apps cost a document and a module load each time they open, and cannot share custom
  elements with the shell; drag and drop, pop-ups and navigation of the top window are not
  available to them.
- The Content Security Policy allows inline styles (`style-src 'unsafe-inline'`), which the shadow
  DOM of plugins uses, in the shell as in the frames.
- The behaviour was checked in Chromium with a harness (isolation of the DOM and of the storage,
  refused external connections, bridge rules, forms); an automated browser test in CI is still to
  be added.
