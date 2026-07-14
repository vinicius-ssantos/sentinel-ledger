# ADR-003 — Immutable double-entry ledger

## Status

Accepted

## Context

Mutable balance rows cannot explain historical financial effects or prove how a correction occurred.

## Decision

Each completed financial effect posts one immutable ledger transaction containing balanced debit and credit entries. Posted entries are never edited or deleted. Corrections create linked compensating transactions. Balance is a rebuildable projection.

## Alternatives considered

- Store only a current balance.
- Edit incorrect entries in place.
- Use the payment state table as the financial ledger.

## Consequences

History is explainable and reconstructable, at the cost of explicit account policy and projection logic. Authorization changes payment availability; capture and refund create the required MVP postings.

## Validation

Balanced-transaction property tests, append-only integration tests, compensating examples, and projection rebuild tests.
