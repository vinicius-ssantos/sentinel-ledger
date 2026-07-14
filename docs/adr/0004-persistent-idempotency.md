# ADR-004 — Persistent idempotency

## Status

Accepted

## Context

Clients retry after timeouts and may send the same command concurrently. Process memory or expiring cache state cannot guarantee one durable effect across restarts.

## Decision

Persist idempotency records in PostgreSQL, uniquely scoped by merchant, operation, and key. Store a canonical request hash, processing state, resource identity, and replayable outcome. The same key with a different hash returns a conflict.

## Alternatives considered

- In-memory deduplication.
- Redis-only keys with TTL.
- Treat every retry as a new request.

## Consequences

The API gains deterministic retry semantics and durable storage cost. Retention must not delete evidence while a legitimate replay or investigation remains possible.

## Validation

Same-request replay, payload conflict, concurrent acquisition, failed request, processing request, and restart tests.
