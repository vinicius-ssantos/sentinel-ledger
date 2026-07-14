# Project Brief

## Product statement

Sentinel Ledger is an educational payment orchestration platform that demonstrates how a backend remains correct under retries, concurrency, provider uncertainty, duplicate callbacks, and reconciliation mismatches.

## Portfolio objective

The project is designed to demonstrate judgment rather than stack breadth. A reviewer should be able to identify:

- explicit business invariants;
- clear aggregate and transaction boundaries;
- persistent idempotency;
- append-only financial records;
- recoverable external integration;
- measured concurrency behavior;
- module boundaries and architectural tests;
- business-oriented observability;
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
- capture all or part of the authorized amount;
- refund all or part of the net captured amount;
- record capture and refund effects in a balanced ledger;
- reject duplicate effects through persistent idempotency;
- show an immutable timeline;
- detect a simple mismatch between internal and provider state.

## Constraints

- one merchant and BRL only;
- no real payment data or credentials;
- no database transaction held across a PSP call;
- PostgreSQL is the source of truth;
- no broker is required before the reliability phase;
- no claims of scale without a reproducible test environment.

## Definition of MVP success

The MVP is successful when the documented happy path and failure scenarios work end to end, every invariant is covered by executable tests, concurrent capture cannot overspend the authorization, duplicate requests do not duplicate effects, and a reconciliation mismatch can be detected and explained.

## Non-goals

This is not a bank, card vault, real gateway, complete accounting suite, antifraud platform, identity provider, or Stripe clone.
