# Architectural Decision Register

ADRs record context, decision, alternatives, consequences, and later superseding decisions. Accepted decisions may change only through a new ADR.

| ID | Decision | Status | Summary |
| --- | --- | --- | --- |
| ADR-001 | Modular monolith first | Accepted | Keep domain modules in one deployable process while boundaries are discovered and verified |
| ADR-002 | PostgreSQL as source of truth | Accepted | Persist payment, ledger, idempotency, audit, and reconciliation state in PostgreSQL |
| ADR-003 | Immutable double-entry ledger | Accepted | Never update or delete posted entries; corrections use compensating transactions |
| ADR-004 | Persistent idempotency | Accepted | Enforce uniqueness by merchant, operation, and key with canonical request hashing |
| ADR-005 | One simulated PSP | Accepted | Exercise external failure modes without real credentials or financial dependencies |
| ADR-006 | BRL in integer minor units | Accepted for MVP | Use minor units while the system supports one fixed-scale currency |
| ADR-007 | Broker deferred | Accepted | Introduce RabbitMQ only with transactional outbox in the reliability phase |
| ADR-008 | No open DB transaction across PSP call | Accepted | Persist pending state, commit, call PSP, and persist the outcome separately |

## ADR template

```markdown
# ADR-NNN — Decision title

## Status

Proposed | Accepted | Superseded

## Context

What problem and forces require a decision?

## Decision

What will the project do?

## Alternatives considered

What credible alternatives were evaluated?

## Consequences

What becomes easier, harder, or constrained?

## Validation

Which tests, metrics, or operational evidence will validate the decision?
```

Individual ADR files will be created when implementation work begins or when a decision requires deeper evidence than this initial register.
