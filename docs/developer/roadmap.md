# Roadmap

The plan from the prototype (requirements MK-001 to MK-024, all done) to a first version that
works like the web version of Teams ([ADR-0026](../adr/0026-teams-like-shell.md) to
[ADR-0030](../adr/0030-global-search-with-pills.md)). Each phase ends with a release that can be
installed and shown; the next phase starts from what the previous one released.

Estimates are in weeks of one person working full time on it, with an AI assistant; they become
calendar time only once the weekly hours are known.

| Phase | Release | Content | Requirements | Weeks |
|---|---|---|---|---|
| F1 Shell | 0.2 | Shell like Teams: app bar, top bar, list pane, menu of the person, settings, UI kit on Fluent UI Web Components, apps per organization, sign-in pages (local and SSO) in the new style, progressive web app, PA theme | MK-025, MK-026, MK-027, MK-030; MK-028, MK-029 | 4–5 |
| F2 Collaboration | 0.3 | Real-time channel, teams as groups with guests, presence, activity feed, `app-teams` (channels, posts, threads, mentions, reactions, tabs), `app-chat`, the assistant as a bot | MK-031, MK-032, MK-034, MK-035, MK-036, MK-038; MK-033, MK-037 | 7–9 |
| F3 Search and push | 0.4 | Global search with pills and providers of the plugins; push notifications of the browser | MK-040; MK-039 | 3–4 |
| F4 Files | 0.5 | S3-compatible storage and file service, `app-files` with files of teams, channels and chats, connectors `ext-files-*` (WebDAV and Nextcloud first, then GeoNode, CKAN, S3), retention with audit | MK-041, MK-042, MK-044; MK-043 | 4–5 |
| F5 Apps of Geoportal | 0.6 | `app-data`, `app-maps`, `app-dashboards`, `app-processes` ported from Geoportal, as apps of the app bar and tabs of the channels; mockups of verticals | in the repositories of the apps | 8–12 |
| Later | | Calendar, eDiscovery, online editing of documents, meetings, OpenSearch | MK-045 | |

In each row the requirements before the semicolon are needed for the release; the others (P2) can
move to the next release without blocking it.

## In every phase

- The manual tests of the phase, written as a test plan before the code and run before the
  release; the automated tests of each requirement (`@Tag("MK-xxx")`).
- Playwright and axe for the screens of the phase, so that the quality gate of the prototype
  (spike S7) is complete by the end of F1.
- The guides of [docs/user](../user/README.md) and of plugin development for what changed, and the
  release notes.

## Order inside the phases

- **F1.** UI kit and theme tokens first (MK-026), then the frame of the shell (MK-025) with the
  sample plugins moved to `rail.app`, then settings (MK-027), apps per organization (MK-030), PWA
  and theme plugins.
- **F2.** Real-time channel (MK-031) and groups (MK-032) first: everything else needs them. Then
  the activity feed (MK-038), `app-chat` (MK-036), which is smaller and proves the real-time path,
  then `app-teams` (MK-034, MK-035), presence and the bot.
- **F4.** Storage and file service (MK-041) before `app-files`; the connector to WebDAV first,
  because Nextcloud and many document services speak it.

## Repositories

`app-teams`, `app-chat`, `app-files`, `app-calendar` and the connectors `ext-files-<service>` are
plugin repositories of the organization ([ADR-0024](../adr/0024-hosting-on-github.md),
[ADR-0025](../adr/0025-github-pages-and-maven-namespace.md)), created with
`@mosaikit/create-plugin` when their phase starts. The kernel, the shell, the UI kit and the
requirements of the platform stay in this repository.
