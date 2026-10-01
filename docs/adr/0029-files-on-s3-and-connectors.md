# 0029. Files on S3-compatible storage, and connectors to external services

- Status: accepted
- Date: 2026-10-01
- Deciders: Massimo Antonini
- Extends [ADR-0027](0027-teams-channels-and-chat.md)

## Context and problem statement

Teams and chats share files, and products such as Geoportal also work on files that live in other
services: GeoNode, Nextcloud, open-data portals, the storage of the administration. Keeping files
in PostgreSQL does not scale, and copying the files of external services creates two versions of
the truth.

## Decision

- **Storage.** The kernel keeps files in an S3-compatible object store (MinIO on premises and in
  the portable, any S3 service in the SaaS), one bucket prefix per organization. Plugins read and
  write files through a file service of `kernel-api`, which applies the rights of the person and
  records the audit; they never hold the credentials of the store.
- **Files app.** The plugin `app-files` shows the files of the person, of teams and channels and
  of chats, with versions, previews and sharing.
- **Connectors.** A connector to an external service is an extension of `app-files`,
  `ext-files-<service>` (GeoNode, WebDAV and Nextcloud, CKAN, another S3): it lists, reads and,
  when allowed, writes the files of that service with the credentials of the person or of the
  organization, without copying them.
- **Retention.** Files follow the retention configured per organization, like messages
  (ADR-0027), with the audit of deletions.

## Consequences

- Installations need an object store; the portable distribution brings MinIO, or uses a directory
  through the same interface when it runs alone.
- Online editing of documents (Collabora, OnlyOffice) can be added later as an extension of
  `app-files`.

## Alternatives considered

- **Files in PostgreSQL.** Simple backups, but large objects and streaming do not fit.
- **The filesystem of the server.** Fine for one node, not for a cluster or the SaaS.
- **Copying the files of external services.** Two versions of each file, and rights out of sync.
