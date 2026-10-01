# 0001. Record architecture decisions

- Status: accepted
- Date: 2026-09-29
- Deciders: Massimo Antonini

## Context and problem statement

Mosaikit exposes a public contract to third-party plugin authors. Decisions that shape this
contract must be traceable, with their reasons, so that they can be revisited with evidence.

## Decision

Every significant decision is recorded as an ADR in `docs/adr/`, in MADR format, numbered
sequentially. A change of decision is a new ADR that supersedes the previous one.

## Consequences

- Reviewers can ask "which ADR covers this?" for any change to `kernel-api` (including the manifest schema) or `sdk/`.
- The proposal document that preceded the implementation is summarized by ADR 0002–0009.
