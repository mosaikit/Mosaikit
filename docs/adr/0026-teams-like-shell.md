# 0026. A shell that works like Microsoft Teams, on Fluent UI Web Components

- Status: accepted
- Date: 2026-10-01
- Deciders: Massimo Antonini
- Refines [ADR-0006](0006-frontend-web-components.md) (shell) and
  [ADR-0020](0020-frontends-in-any-framework.md) (UI kit)
- Refined by [ADR-0032](0032-fluent-ui-and-themes.md): the theme of public administrations is
  built in, and Fluent UI is used for the whole interface

## Context and problem statement

The shell of the prototype is a page with a header, a launcher of apps and the assistant. The
people who will use Mosaikit work every day in Microsoft Teams: they expect its structure (a bar
of apps on the left, a list and a work area, search at the top, activity and chats always at hand)
and its behaviour. A shell that looks like every other admin console makes them learn a new
model for each product built on Mosaikit.

## Decision

- **Structure.** The shell has the structure of the web version of Teams: an app bar on the left
  (rail), a top bar with the global search and the menu of the person, a list pane and the work
  area. At phone width the rail becomes a bottom navigation. No name, logo or icon of Microsoft.
- **Apps in the rail.** The rail shows the apps of the kernel and of the collaboration plugins
  (Activity, Chat, Teams, Calendar, Files) and then the apps of the other plugins. A plugin adds
  an app with the extension point `rail.app`, which replaces `launcher.app`; the shell keeps
  reading `launcher.app` during the 0.x versions. Administrators pin, order and hide apps per
  organization.
- **Menu of the person.** Presence, switch of organization when the person belongs to several
  (ADR-0016), settings and sign-out.
- **Settings.** Theme (light, dark, high contrast, and themes of `theme-*` plugins), language and
  active organization are kernel settings; plugins add sections with `settings.section`.
- **UI kit.** `@mosaikit/ui` wraps Fluent UI Web Components (MIT): the shell and the plugins that
  want the same look use it; the theme tokens `--mk-*` stay the contract, and the kit maps them on
  the Fluent design tokens. Plugins keep their own framework (ADR-0020).
- **Progressive web app.** The shell is responsive and installable (manifest, service worker);
  no desktop application.

## Consequences

- Every product on Mosaikit, Geoportal first, looks and behaves like Teams without being
  collaboration software itself: the kernel stays agnostic, collaboration comes as plugins
  (ADR-0027).
- The kernel grows a few services the shell needs: settings, presence, activity, search
  (ADR-0028, ADR-0030).
- Fluent UI Web Components becomes a dependency of the UI kit; a plugin that does not use the kit
  still follows the theme tokens.
- `launcher.app` is deprecated; the sample plugins move to `rail.app`.

## Alternatives considered

- **Keeping the current shell and adding pages.** Cheaper, but each product would invent its own
  navigation, and people would not recognise it.
- **Embedding Mosaikit apps in Teams.** It ties public bodies to a Microsoft 365 tenant and does not
  work on premises or offline.
- **Material or Bootstrap Italia as the kit.** Bootstrap Italia stays available as a `theme-*`
  plugin for the PA; Fluent UI Web Components is the closest match to the Teams experience.
