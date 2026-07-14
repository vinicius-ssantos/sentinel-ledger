# ADR-005 — Provider uncertainty and reconciliation are core behavior

## Status

Accepted

## Context

A PSP can process an operation even when the caller never receives the response. Mapping transport timeout directly to failure can cause duplicate or contradictory financial behavior.

## Decision

Use a persist-call-persist workflow with no open database transaction across the network call. Represent uncertain outcomes explicitly. Recover through provider status lookup, callbacks, and a deduplicated reconciliation case that preserves evidence and audited resolution.

## Alternatives considered

- Keep the database transaction open during the PSP call.
- Mark every timeout as failed.
- Retry blindly until a response arrives.
- Treat reconciliation as a post-MVP reporting feature.

## Consequences

The state model and operations workflow become richer, but the system can explain and repair realistic uncertainty safely. Reconciliation is delivered with ledger/concurrency correctness rather than after messaging infrastructure.

## Validation

Timeout-before-processing, timeout-after-processing, duplicate callback, delayed callback, repeated reconciliation, restart, and audited resolution scenarios.
