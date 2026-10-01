# 0027. Teams, channels and chats: groups in the kernel, collaboration in plugins

- Status: accepted
- Date: 2026-10-01
- Deciders: Massimo Antonini
- Extends [ADR-0005](0005-organizations-and-identity.md) and
  [ADR-0016](0016-membership-of-several-organizations.md); uses
  [ADR-0019](0019-plugins-extending-plugins.md)

## Context and problem statement

A shell like Teams (ADR-0026) needs its model: teams of people, channels inside them, tabs in the
channels, chats. Mosaikit has organizations (tenants) and memberships, not groups. The kernel must
stay agnostic of the domain, and collaboration is a product feature, not a platform one.

## Decision

- **Organization = tenant**, as today.
- **Team = group of the organization**, in the kernel: name, visibility (public, private),
  members with a role (owner, member, guest). A guest is a person of another organization
  (ADR-0016) admitted to that team only. Groups are a kernel concept because every plugin needs
  them to share data and to grant rights; they are visible to the plugins through `kernel-api`
  and to row-level security (ADR-0018).
- **Channels, posts and tabs** are the plugin `app-teams`: channels of a team (General plus
  standard and private ones), posts with threads, mentions and reactions, and tabs. The tabs Posts
  and Files are its own; other plugins add tabs through its extension point `channel.tab`, as they
  extend any other plugin.
- **Chats** 1:1 and of groups of people are the plugin `app-chat`; posts and messages can carry
  cards, structured messages that plugins publish.
- **The assistant is the bot.** The assistant of the kernel (ADR-0022) appears as a chat contact
  and answers when mentioned in a channel, with the rights of the person who asks; its changes stay
  drafts until confirmed (ADR-0017).
- **Retention.** Messages are kept for a period configured per organization, with the audit of
  deletions; eDiscovery comes later.

## Consequences

- Products on Mosaikit get collaboration by installing `app-teams` and `app-chat`, or do without it.
- The kernel gains groups and the plugin API gains their read model; row-level security gains a
  policy for data shared with a group.
- `app-teams` and `app-chat` depend on the real-time channel and the activity feed of the kernel
  (ADR-0028), and store files through the file service (ADR-0029).

## Alternatives considered

- **Teams as organizations.** It would mix tenants and groups, and guests would need a membership of
  the whole organization.
- **Collaboration in the kernel.** Simpler to build, but every product would carry it and the
  kernel would stop being agnostic.
- **Matrix or Mattermost behind the plugins.** Mature protocols, but one more server to install,
  another identity and another permission model; possible later as a connector.
