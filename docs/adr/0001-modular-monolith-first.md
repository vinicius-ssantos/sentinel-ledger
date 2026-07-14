# ADR-001 — Modular monolith first

## Status

Accepted

## Context

Payment, ledger, idempotency, audit, and reconciliation rules need strong local consistency while their boundaries are still being discovered. Starting with independently deployed services would add network and operational failure modes before they provide portfolio evidence.

## Decision

Build one Spring Boot deployable with functional modules verified by Spring Modulith and focused ArchUnit rules. Modules expose explicit public APIs and keep implementation packages internal.

## Alternatives considered

- Microservices from the first commit.
- A package-by-technical-layer monolith.

## Consequences

Local transactions and end-to-end tests remain simple. Module ownership must still be enforced deliberately. A module may be extracted later only with measured coupling, scaling, or ownership evidence.

## Validation

Module verification, cycle detection, internal-package access tests, and generated module documentation run in CI.
