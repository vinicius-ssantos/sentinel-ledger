# ADR-002 — PostgreSQL as source of truth

## Status

Accepted

## Context

Payment limits, idempotency, immutable ledger entries, audit evidence, and reconciliation cases require durable constraints and transactional behavior.

## Decision

PostgreSQL is authoritative for business state. Tests that depend on persistence semantics use a real PostgreSQL container rather than an in-memory substitute.

## Alternatives considered

- Redis as the idempotency authority.
- Independent stores per module in the MVP.
- H2 for integration tests.

## Consequences

The project can prove restart safety and database-backed concurrency. Schema ownership and migrations must remain explicit. Rebuildable projections may optimize reads but cannot replace authoritative records.

## Validation

Flyway-from-empty tests, Testcontainers integration tests, concurrency tests, and projection rebuild checks.
