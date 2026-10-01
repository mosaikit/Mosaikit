# 0030. Global search with pills, on PostgreSQL full-text and providers of the plugins

- Status: accepted
- Date: 2026-10-01
- Deciders: Massimo Antonini
- Extends [ADR-0026](0026-teams-like-shell.md)

## Context and problem statement

The search box of the shell must find people, messages, files and the data of every app, and let
people narrow the search to one kind with a pill, as in Teams. The kernel does not know what the
apps contain, and an external search engine is one more server to run.

## Decision

- **Providers.** A plugin declares a search provider with the extension point `search.provider`
  (id, label of the pill, endpoint of its API). The kernel provides people and teams; `app-chat`,
  `app-teams` and `app-files` provide messages and files; any app can provide its data.
- **Pills.** The search box shows one pill per provider (All, People, Messages, Files, Data, Maps,
  …); All asks every provider in parallel and merges the first results of each, a pill asks one
  provider with paging.
- **Engine.** Providers search their own tables with PostgreSQL full-text search (`tsvector`,
  dictionaries of the languages of the installation), under row-level security, so a result is
  never shown to someone who cannot read it.

## Consequences

- No search server to install; the quality of the ranking is that of PostgreSQL, good for the
  sizes of the installations on premises.
- A provider that is slow is cut at a time limit and shown as incomplete, without blocking the
  others.
- OpenSearch can replace PostgreSQL behind the same providers if the SaaS needs it.

## Alternatives considered

- **OpenSearch from the start.** Better ranking and facets, but a cluster to run and an index to
  keep in sync with row-level security.
- **A central index in the kernel.** The kernel would have to understand the data of the plugins.
