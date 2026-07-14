# Architecture

## Style

Sentinel Ledger starts as a Spring Boot modular monolith. Functional modules live in one deployable process and one PostgreSQL database while retaining explicit APIs, internal packages, dependency rules, and module-focused tests.

This choice favors local transactions and fast domain discovery. Distribution is a later response to measured needs, not an initial requirement.

## Module boundaries

| Module | Owns | May depend on |
| --- | --- | --- |
| `payments` | Payment intents, authorizations, captures, refunds | merchant API, PSP port, ledger API, idempotency API, audit API |
| `ledger` | Ledger accounts, transactions, entries, projections | audit API |
| `reconciliation` | Cases, mismatch rules, resolutions | payments API, ledger API, PSP port, audit API |
| `idempotency` | Keys, request hashes, stored outcomes | shared technical primitives only |
| `integration.psp` | Provider requests, results, callbacks | payments public commands/events |
| `merchant` | Merchant configuration and access context | shared technical primitives only |
| `audit` | Audit events and actor evidence | shared technical primitives only |
| `observability` | Cross-cutting telemetry configuration | public events and technical adapters |

Spring Modulith verification and ArchUnit tests will reject cycles, internal package access, and dependencies not listed by the module policy.

## Persistence boundaries

PostgreSQL is authoritative for payment state, idempotency, ledger entries, audit evidence, and reconciliation cases.

Ledger entries are append-only. A balance may be maintained as a rebuildable projection for efficient reads, but the projection is not allowed to replace the ledger as source of truth.

## External integration boundary

The simulated PSP is outside the database transaction. Authorization follows a persist-call-persist workflow so the system can represent provider uncertainty instead of holding locks while waiting on the network.

The PSP adapter must expose explicit results such as approved, declined, timeout/unknown, and protocol failure. Provider-specific payloads must not leak into the payment domain.

## Concurrency strategy

The first implementation will compare optimistic locking and atomic conditional updates for capture and refund. The chosen approach must make overspending impossible and return deterministic conflicts to losing requests.

Correctness relies on database constraints and transaction semantics, not an in-memory or Redis lock.

## Event strategy

Domain events may be used for module decoupling inside the monolith. External broker publication is deferred to the reliability phase.

When RabbitMQ is introduced, a transactional outbox will persist the business change and publication intent in the same local transaction. Consumers will use an inbox or equivalent unique processing record to tolerate duplicate delivery.

## Authentication and authorization

Authentication is infrastructure for this project, not its product domain. Spring Security will protect merchant and operator APIs. Merchant identity must be derived from the authenticated principal and checked at resource boundaries.

## Deployment evolution

1. Local Maven application plus PostgreSQL/Testcontainers.
2. Containerized application and PostgreSQL for demonstration.
3. Separate API and worker processes only after the outbox phase.
4. Managed database and broker for public deployment.
5. Kubernetes only if a measured operational requirement justifies it.
