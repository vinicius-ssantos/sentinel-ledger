# Architectural Decision Register

ADRs record context, decision, alternatives, consequences, and later superseding decisions. Accepted decisions may change only through a new ADR.

| ID | Decision | Status | Summary |
| --- | --- | --- | --- |
| [ADR-001](0001-modular-monolith-first.md) | Modular monolith first | Accepted | Keep domain modules in one deployable process while boundaries are discovered and verified |
| [ADR-002](0002-postgresql-source-of-truth.md) | PostgreSQL as source of truth | Accepted | Persist authoritative business state in PostgreSQL and test real semantics |
| [ADR-003](0003-immutable-double-entry-ledger.md) | Immutable double-entry ledger | Accepted | Never edit posted entries; corrections use compensating transactions |
| [ADR-004](0004-persistent-idempotency.md) | Persistent idempotency | Accepted | Scope durable keys and compare canonical request hashes |
| [ADR-005](0005-provider-uncertainty-and-reconciliation.md) | Provider uncertainty and reconciliation | Accepted | Persist-call-persist, explicit unknown state, and evidence-based recovery |
| [ADR-006](0006-defer-distributed-infrastructure.md) | Defer distributed infrastructure | Accepted | Prove the core before broker, independent services, or Kubernetes |
| ADR-007 | BRL in integer minor units | Accepted for MVP | Use minor units while the system supports one fixed-scale currency |

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

New decisions receive individual files when they materially change correctness, trust boundaries, persistence, integration, or deployment strategy. A superseded ADR remains in the repository and links to its replacement.
