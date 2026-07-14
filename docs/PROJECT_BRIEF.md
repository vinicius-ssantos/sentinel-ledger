# Project Brief

## Product statement

Sentinel Ledger is an educational payment orchestration platform that demonstrates how a backend remains correct under retries, concurrency, provider uncertainty, duplicate callbacks, and reconciliation mismatches.

Its central claim is that financial truth remains bounded, durable, reconstructable, and explainable even when the network and provider do not behave cleanly.

## Portfolio objective

The project is designed to demonstrate judgment rather than stack breadth. A reviewer should be able to identify:

- explicit business invariants;
- clear aggregate and transaction boundaries;
- persistent idempotency;
- append-only financial records;
- recoverable external integration;
- explicit uncertain states rather than guessed outcomes;
- measured concurrency behavior;
- module boundaries and architectural tests;
- business-oriented observability;
- an investigation workflow connecting payment, provider, ledger, reconciliation, and audit evidence;
- progressive introduction of distributed infrastructure.

## Personas

### Merchant developer

Creates and inspects payment operations through a stable API and retries safely after network uncertainty.

### Operations analyst

Inspects payment timelines, provider mismatches, failed webhooks, and reconciliation cases.

### Platform operator

Monitors health, latency, queue depth, duplicate processing, and business error rates.

## MVP capabilities

- create a payment intent;
- authorize it against a simulated PSP;
- recover an authorization whose final provider result was initially unknown;
- capture all or part of the authorized amount;
- refund all or part of the net captured amount;
- record capture and refund effects in a balanced ledger;
- reject duplicate effects through persistent idempotency;
- show an immutable timeline;
- detect, deduplicate, inspect, and resolve a mismatch between internal and provider state.

## Constraints

- one merchant and BRL only;
- no real payment data or credentials;
- no database transaction held across a PSP call;
- PostgreSQL is the source of truth;
- no broker is required before the reliability phase;
- no claims of scale without a reproducible test environment.

## Definition of MVP success

The MVP is successful when the documented happy path and failure scenarios work end to end, every invariant is linked to executable evidence, concurrent capture cannot overspend the authorization, duplicate requests do not duplicate effects, an unknown provider outcome can be recovered, and a reconciliation mismatch can be detected and explained after restart.

## Portfolio acceptance scorecard

| Dimension | Acceptance evidence |
| --- | --- |
| Financial correctness | Balanced immutable ledger, bounded capture/refund, rebuildable projection |
| Retry safety | Persistent idempotency across concurrency and restart |
| External uncertainty | Timeout-after-processing recovered from provider evidence |
| Operational recovery | Deduplicated mismatch case and audited resolution |
| Explainability | Timeline connects API, provider, payment, ledger, audit, and reconciliation |
| Engineering restraint | Distributed infrastructure appears only after a documented need |
| Reproducibility | Clean-checkout demo and benchmark with environment and invariant checks |

## Non-goals

This is not a bank, card vault, real gateway, complete accounting suite, antifraud platform, identity provider, or Stripe clone.
