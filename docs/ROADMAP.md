# Delivery Roadmap

The roadmap is outcome-based. Dates and performance claims will be added only when capacity and test environments are known.

## Phase 0 — Specification and foundation

### Deliverables

- project brief and non-goals;
- domain model and worked payment/ledger examples;
- normative payment transition table;
- MVP chart of accounts and balanced posting examples;
- persistent idempotency lifecycle and RFC 9457 error catalog;
- invariant catalog with stable IDs and proof levels;
- state transition table;
- deterministic provider failure model;
- lightweight threat model;
- golden demo contract;
- initial ADR register;
- API outline and error model;
- prioritized GitHub backlog;
- repository contribution and security policies.

### Exit criteria

- all MVP invariants are unambiguous and mapped to planned executable evidence;
- authorization, capture, refund, and reconciliation examples agree;
- unknown provider outcomes and recovery transitions are explicit;
- unresolved decisions are tracked as issues rather than hidden assumptions.

### Normative Phase 0 contracts

- [Payment state machine](PAYMENT_STATE_MACHINE.md)
- [Ledger policy and posting examples](LEDGER_POSTINGS.md)
- [Persistent idempotency and error contract](IDEMPOTENCY_AND_ERRORS.md)
- [Engineering invariant catalog](INVARIANTS.md)
- [Failure model](FAILURE_MODEL.md)
- [Threat model](THREAT_MODEL.md)
- [Golden demo contract](DEMO_RUNBOOK.md)

## Phase 1 — Transactional core

### Deliverables

- Java 25 and Spring Boot 4.1 bootstrap;
- Spring Modulith package boundaries;
- PostgreSQL, Flyway, and Testcontainers;
- Merchant and PaymentIntent models;
- create/read payment intent API;
- authorization workflow with a deterministic failure-capable simulated PSP;
- persistent idempotency;
- OpenAPI and baseline CI.

### Exit criteria

- approval, decline, timeout-before-processing, timeout-after-processing, safe retry, duplicate callback, and key conflict scenarios pass end to end;
- no database transaction remains open during the PSP call;
- uncertain results remain explicit until provider evidence resolves them;
- module verification runs in CI.

## Phase 2 — Financial correctness and recovery

### Deliverables

- chart of accounts for the MVP;
- balanced LedgerTransaction and LedgerEntry model;
- full and partial capture;
- full and partial refund;
- audit evidence;
- optimistic locking or conditional-update strategy;
- concurrency and property-oriented tests;
- payment timeline;
- provider/internal mismatch detection;
- deduplicated reconciliation cases;
- audited operator resolution and compensating action.

### Exit criteria

- concurrent operations cannot exceed authorization or refundable value;
- unbalanced transactions cannot be persisted;
- corrections use compensating entries;
- ledger state is reconstructable from immutable entries;
- timeout-after-processing reaches a proven final state without duplicate effects;
- repeated reconciliation creates one open case per mismatch;
- restart preserves mismatch evidence and resolution history.

## Phase 3 — Async reliability and observability

### Deliverables

- transactional outbox;
- RabbitMQ publisher and worker;
- inbox/deduplication;
- retry with backoff and DLQ;
- signed webhook delivery;
- structured logs, traces, metrics, and dashboards.

### Exit criteria

- process restart between commit and publication loses no event;
- duplicate delivery applies no duplicate effect;
- failed delivery is observable and recoverable;
- one payment is traceable across API, database, PSP, outbox, broker, webhook, and reconciliation evidence;
- telemetry contains no secrets or unbounded business identifiers as metric labels.

## Phase 4 — Portfolio demonstration

### Deliverables

- small investigation-focused Next.js operations UI;
- connected payment, provider attempt, ledger, reconciliation, audit, and webhook views;
- clean-checkout golden demo automation;
- reproducible k6 report;
- architecture and trade-off narrative;
- economical public deployment.

### Exit criteria

- a reviewer can understand the business problem in under one minute;
- the demo visibly connects API operations to ledger and reconciliation evidence;
- the six golden scenarios complete from synthetic seed data after a clean checkout;
- every demo scenario exposes correlation, audit, and final invariant evidence;
- every benchmark states environment, data, method, and limitations.

## Portfolio completion gate

The project is not called complete until all of the following are true:

- the invariant catalog is linked to executable evidence;
- the golden demo covers retry, key conflict, concurrency, provider uncertainty, compensation, reconciliation, and restart;
- the operations UI investigates backend evidence instead of duplicating domain rules;
- security checks and known limitations are published without compliance claims;
- performance reports include final correctness assertions;
- a reviewer can reproduce the result without private services or credentials.

## Deferred candidates

Multi-PSP routing, chargebacks, multiple currencies, subscriptions, multi-tenancy, native images, independent workers, and Kubernetes require new ADRs and evidence that they solve an actual limitation.
