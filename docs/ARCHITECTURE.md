# Architecture

## Style

Sentinel Ledger starts as a Spring Boot modular monolith. Functional modules live in one deployable process and one PostgreSQL database while retaining explicit APIs, internal packages, dependency rules, and module-focused tests.

This choice favors local transactions and fast domain discovery. Distribution is a later response to measured needs, not an initial requirement.

## Architecture drivers

In priority order:

1. preserve monetary and state invariants;
2. make retries safe across concurrency and restart;
3. represent and recover provider uncertainty;
4. preserve immutable evidence and explain outcomes;
5. keep the project reproducible for reviewers;
6. add operational scale only when measured evidence requires it.

## Module boundaries

| Module | Owns | May depend on |
| --- | --- | --- |
| `money` | Immutable monetary amounts and explicit currency metadata | JDK only |
| `payments` | Payment intents, authorizations, captures, refunds, and the provider-neutral PSP port | money API, merchant API, ledger API, idempotency API, audit API, outbox API, webhooks API |
| `ledger` | Ledger accounts, transactions, entries, projections | money API, audit API |
| `reconciliation` | Cases, mismatch rules, resolutions | money API, payments API, ledger API, PSP port, audit API |
| `idempotency` | Keys, request hashes, stored outcomes | shared technical primitives only |
| `integration.psp` | Provider requests, results, callbacks, and the simulated provider adapter | payments PSP port and public events |
| `merchant` | Merchant configuration and access context | shared technical primitives only |
| `audit` | Audit events and actor evidence | shared technical primitives only |
| `outbox` | Publication intents and their claim/publish/complete lifecycle | shared technical primitives only |
| `integration.messaging` | RabbitMQ topology, the outbox publisher adapter, and the dispatch consumer's inbox | outbox API, webhooks API |
| `webhooks` | Signed webhook delivery, delivery history, and the receiver-side signature-verification algorithm | shared technical primitives only |
| `observability` | Correlation propagation and structured logging configuration | shared technical primitives only (today) |

Spring Modulith 2.1 verification runs as part of `mvn verify` and rejects module cycles, access to another module's internal packages, and dependencies not listed by each module's `@ApplicationModule` policy. Detection is explicitly annotated so `integration.psp` remains a first-class module instead of being folded into an intermediate `integration` package. Module APIs belong in their root packages; implementation details belong in subpackages such as `internal`.

The `money` module is a deliberately small shared domain kernel, not a generic utility package. It owns only monetary representation and exact arithmetic so payments and ledger can share one invariant-preserving type without depending on each other.

ArchUnit remains available for future rules that complement rather than duplicate Spring Modulith verification.

## Persistence boundaries

PostgreSQL is authoritative for payment state, idempotency, ledger entries, audit evidence, and reconciliation cases.

Ledger entries are append-only. A balance may be maintained as a rebuildable projection for efficient reads, but the projection is not allowed to replace the ledger as source of truth.

## External integration boundary

The simulated PSP is outside the database transaction. Authorization follows a persist-call-persist workflow so the system can represent provider uncertainty instead of holding locks while waiting on the network.

The PSP adapter must expose explicit results such as approved, declined, timeout/unknown, and protocol failure. Provider-specific payloads must not leak into the payment domain. Deterministic test/demo controls cover timeout before processing, timeout after processing, delayed/duplicate/out-of-order callbacks, and mismatched status.

## Reconciliation boundary

Reconciliation is part of the financial correctness proof, not a reporting afterthought. It compares internal payment/ledger evidence with provider evidence, fingerprints mismatches, creates at most one open case per divergence, and records an append-only resolution history. It never edits prior ledger entries; financial repair uses a compensating transaction.

## Observability boundary

`observability` owns only correlation propagation (an `X-Correlation-Id` request/response header placed in the logging MDC ahead of the security filter chain) and structured JSON console logging. Business metrics are not routed through this module: each owning module registers its own Micrometer counters, gauges, and timers directly, tagged exclusively by fixed enum/outcome values so cardinality cannot grow with traffic — the same posture as this document's persistence and reconciliation boundaries applied to telemetry. Distributed tracing is deferred; see [OBSERVABILITY.md](OBSERVABILITY.md) for the full metric catalog, the redaction guarantee, and the dashboard/alert artifacts checked in as dashboard-as-code rather than deployed configuration.

## Concurrency strategy

The first implementation will compare optimistic locking and atomic conditional updates for capture and refund. The chosen approach must make overspending impossible and return deterministic conflicts to losing requests.

Correctness relies on database constraints and transaction semantics, not an in-memory or Redis lock.

Every concurrency benchmark must run final invariant assertions. Throughput without correctness verification is not an accepted result.

## Event strategy

Domain events may be used for module decoupling inside the monolith. External broker publication is deferred to the reliability phase.

The `outbox` module persists the business change and its publication intent in the same local transaction (`capture`/`refund` today) and dispatches through a separate claim/publish/complete cycle, so a crash between commit and delivery loses no event and a slow or failing publish never holds a lock. Delivery is at-least-once; consumers must tolerate duplicates.

`integration.messaging` implements the outbox's publisher port against RabbitMQ, behind `sentinel.messaging.enabled` (default off, so no module in the codebase requires a broker to run its own tests). A topic exchange carries every outbox event; the dispatch queue's consumer retries a failing message with bounded exponential backoff, then rejects it to a dead-letter exchange/queue instead of retrying forever. A publish that the broker never confirms (outage, unreachable) throws back into the outbox worker, which leaves the record claimed for retry rather than marking it published, so a broker outage delays delivery without losing the event.

## Webhook delivery boundary

`webhooks` turns a consumed outbox event into a signed, timestamped HTTP callback to the single MVP merchant's registered endpoint (`Sentinel-Signature: t=<unix seconds>,v1=<HMAC-SHA256 hex>`, signed over `timestamp.deliveryId.body`). Delivery history is persisted per event id, so a redelivered message is deduplicated by delivery *status* rather than by mere consumption: `integration.messaging`'s consumer checks whether an event already delivered before calling this module again, so a retried AMQP message never re-notifies the merchant for an effect that already applied, but a message that failed to deliver is retried until the consumer's own retry budget is exhausted — at which point the delivery is marked failed and the message dead-letters. Both outcomes are queryable and surface on the payment timeline. `WebhookSignatureVerifier` is the reference a receiver implementation follows to authenticate a callback and reject an invalid, expired, or replayed one; replay rejection itself needs a durable seen-delivery-id store only the receiver can own, so it is demonstrated by composition in that module's tests rather than built into the verifier.

## Modern Java policy

Modern Java features are used only where they clarify the model or produce measured operational value. Records are preferred for immutable boundary types. Sealed hierarchies may model closed outcome sets. Virtual threads are limited to suitable blocking I/O and must be measured. `ScopedValue` may propagate correlation context but is not an authorization or tenant-isolation mechanism. Preview features are not required for the baseline build.

## Authentication and authorization

Authentication is infrastructure for this project, not its product domain. Spring Security will protect merchant and operator APIs. Merchant identity must be derived from the authenticated principal and checked at resource boundaries.

## Deployment evolution

1. Local Maven application plus PostgreSQL/Testcontainers.
2. Containerized application and PostgreSQL for demonstration.
3. Separate API and worker processes only after the outbox phase.
4. Managed database and broker for public deployment.
5. Kubernetes only if a measured operational requirement justifies it.
