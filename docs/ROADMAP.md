# Delivery Roadmap

The roadmap is outcome-based. Dates and performance claims will be added only when capacity and test environments are known.

## Phase 0 — Specification and foundation

### Deliverables

- project brief and non-goals;
- domain model and worked payment/ledger examples;
- state transition table;
- initial ADR register;
- API outline and error model;
- prioritized GitHub backlog;
- repository contribution and security policies.

### Exit criteria

- all MVP invariants are unambiguous;
- authorization, capture, refund, and reconciliation examples agree;
- unresolved decisions are tracked as issues rather than hidden assumptions.

## Phase 1 — Transactional core

### Deliverables

- Java 25 and Spring Boot 4.1 bootstrap;
- Spring Modulith package boundaries;
- PostgreSQL, Flyway, and Testcontainers;
- Merchant and PaymentIntent models;
- create/read payment intent API;
- authorization workflow with simulated PSP;
- persistent idempotency;
- OpenAPI and baseline CI.

### Exit criteria

- success, decline, timeout/unknown, safe retry, and key conflict scenarios pass end to end;
- no database transaction remains open during the PSP call;
- module verification runs in CI.

## Phase 2 — Ledger and concurrency

### Deliverables

- chart of accounts for the MVP;
- balanced LedgerTransaction and LedgerEntry model;
- full and partial capture;
- full and partial refund;
- audit evidence;
- optimistic locking or conditional-update strategy;
- concurrency and property-oriented tests;
- payment timeline.

### Exit criteria

- concurrent operations cannot exceed authorization or refundable value;
- unbalanced transactions cannot be persisted;
- corrections use compensating entries;
- ledger state is reconstructable from immutable entries.

## Phase 3 — Reliability and reconciliation

### Deliverables

- transactional outbox;
- RabbitMQ publisher and worker;
- inbox/deduplication;
- retry with backoff and DLQ;
- signed webhook delivery;
- reconciliation job and operator resolution;
- structured logs, traces, metrics, and dashboards.

### Exit criteria

- process restart between commit and publication loses no event;
- duplicate delivery applies no duplicate effect;
- failed delivery is observable and recoverable;
- provider mismatch produces a reconciliation case.

## Phase 4 — Portfolio demonstration

### Deliverables

- small Next.js operations UI;
- payment timeline, ledger entries, reconciliation, and webhook views;
- reproducible k6 report;
- architecture and trade-off narrative;
- economical public deployment.

### Exit criteria

- a reviewer can understand the business problem in under one minute;
- the demo visibly connects API operations to ledger and reconciliation evidence;
- every benchmark states environment, data, method, and limitations.

## Deferred candidates

Multi-PSP routing, chargebacks, multiple currencies, subscriptions, multi-tenancy, native images, independent workers, and Kubernetes require new ADRs and evidence that they solve an actual limitation.
