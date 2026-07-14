# ADR-006 — Defer distributed infrastructure

## Status

Accepted

## Context

RabbitMQ, independent workers, multiple PSPs, and Kubernetes add operational breadth but do not establish the initial financial correctness claims.

## Decision

Deliver the transactional core, ledger, concurrency, and reconciliation proof before introducing a broker. Add RabbitMQ only behind a transactional outbox and measurable delivery requirement. Defer microservices and Kubernetes until evidence justifies them.

## Alternatives considered

- Event-driven microservices from the beginning.
- Kafka or RabbitMQ as a mandatory local dependency.
- Kubernetes-first deployment.

## Consequences

Early development stays focused and reproducible. Later messaging work must show a concrete outcome: no lost publication intent, duplicate-tolerant consumption, bounded retries, and visible dead letters.

## Validation

Phase exit criteria, outbox restart test, duplicate-delivery test, and documented ADR before any new distributed component.
