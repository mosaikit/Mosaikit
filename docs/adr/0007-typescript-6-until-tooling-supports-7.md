# 0007. TypeScript 6 until the tooling supports TypeScript 7

- Status: accepted
- Date: 2026-09-29
- Deciders: Massimo Antonini

## Context and problem statement

TypeScript 7 (native compiler) is released but has no programmatic API until 7.1.
typescript-eslint 8.71 requires TypeScript `<6.1`, and Angular 22 accepts only TypeScript 6.0.

## Decision

Use TypeScript 6.0 in all workspaces. Move to TypeScript 7 when typescript-eslint supports it.

## Consequences

The proposal document's choice of TypeScript 7 is deferred; nothing in the code depends on it.
