# 0028. Real time, presence, activity and notifications in the kernel

- Status: accepted
- Date: 2026-10-01
- Deciders: Massimo Antonini
- Extends [ADR-0026](0026-teams-like-shell.md) and [ADR-0027](0027-teams-channels-and-chat.md)

## Context and problem statement

Chats, channels, presence and the activity feed need the server to push changes to the browser.
Today the event bus of the plugins (MK-009) lives in the page, and nothing reaches the browser
from the server. Installations on premises have up to about 50 people at once on one node; the
SaaS up to about 1000, on several nodes.

## Decision

- **One WebSocket per page.** The shell opens one WebSocket to the kernel (`/api/v1/live`),
  authenticated like the API, for the active organization. Plugins subscribe to topics through the
  context of the frontend; the kernel checks that the person may read each topic.
- **Fan-out.** On one node the kernel distributes events with PostgreSQL `LISTEN/NOTIFY`, so that a
  change committed by any request reaches the sockets; in a cluster a NATS server replaces it,
  behind the same interface. No broker for the installations on premises.
- **Presence** (available, busy, away, offline) is kept by the kernel from the sockets and from
  the choice of the person.
- **Activity and notifications.** Plugins send notifications to people or groups through
  `kernel-api`; the kernel stores them in the activity feed, pushes them on the socket, and sends a
  Web Push notification (VAPID) to the browsers that allowed it. People choose which notifications
  they receive.

## Consequences

- The kernel keeps open connections: the Helm chart needs sticky sessions only for the sockets,
  and the cluster needs NATS.
- The event bus of the page stays for events between plugins in the same page; the WebSocket
  carries events from the server.
- Tests need a real browser for the socket and push paths (Playwright, already planned by S7).

## Alternatives considered

- **Server-sent events.** Simpler, but one-way: typing indicators and acknowledgements would need
  extra requests.
- **Polling.** No new infrastructure, but latency and load are not acceptable for chat.
- **A broker on every installation.** Not needed under about 50 people on one node.
