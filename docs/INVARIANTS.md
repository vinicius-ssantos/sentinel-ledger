# Engineering Invariants

This document is the correctness contract for Sentinel Ledger. An invariant is not complete when it exists only in prose: every implemented rule must be linked to executable evidence.

## Proof levels

| Level | Evidence |
| --- | --- |
| Domain | Fast unit or property-oriented tests over pure domain behavior |
| Persistence | PostgreSQL integration tests proving constraints and transaction behavior |
| Concurrency | Multiple real transactions racing against the same resource |
| Recovery | Restart, retry, timeout, duplicate, and reconciliation scenarios |
| Demonstration | A reproducible scenario visible through API, timeline, ledger, audit, and telemetry |

## Invariant catalog

| ID | Rule | Primary enforcement | Required evidence |
| --- | --- | --- | --- |
| `PAY-001` | Total successful capture never exceeds the authorized amount | Payment aggregate plus optimistic versioning or conditional update | Domain, PostgreSQL, concurrency |
| `PAY-002` | Total successful refund never exceeds the captured amount not previously refunded | Payment aggregate plus database concurrency control | Domain, PostgreSQL, concurrency |
| `PAY-003` | Only documented payment state transitions can succeed | Explicit transition policy | Parameterized transition tests |
| `PAY-004` | The MVP accepts no new capture after the first successful refund | Transition policy and aggregate guard | Domain, API contract, and concurrency tests |
| `LED-001` | Every posted ledger transaction has equal debit and credit totals | Ledger posting boundary validates the complete transaction before persistence | Property-oriented and PostgreSQL tests |
| `LED-002` | Posted entries are immutable | Append-only repository/API policy and restricted update/delete paths | Integration and security tests |
| `LED-003` | Financial correction creates a compensating transaction | Ledger command model | Worked examples and integration tests |
| `LED-004` | A balance projection can be rebuilt from authoritative entries | Deterministic projection logic | Rebuild test from an empty projection |
| `IDEM-001` | One merchant, operation, and idempotency key can produce at most one business effect | Unique database key, request hash, and stored outcome | Retry, concurrency, and restart tests |
| `IDEM-002` | Reusing a key with a different canonical request is rejected | Canonical hash comparison | API contract and concurrency tests |
| `INT-001` | Provider uncertainty is represented explicitly and never guessed as success or failure | Persist-call-persist workflow and `UNKNOWN` state | Timeout-after-processing recovery test |
| `INT-002` | Duplicate or out-of-order provider evidence cannot reapply an effect | Provider event identity and transition guards | Duplicate and reordered callback tests |
| `REC-001` | The same unresolved mismatch does not create duplicate open cases | Reconciliation fingerprint and uniqueness policy | Repeated-run integration test |
| `REC-002` | Resolution preserves the original evidence and actor reason | Append-only case history and audit event | Operator resolution test |
| `AUD-001` | Every sensitive business or operator command leaves redacted audit evidence | Audit API in the local business transaction | Integration and timeline tests |

## Change rule

A pull request that changes one of these rules must:

1. reference the invariant ID;
2. update the domain/API documentation;
3. add or update executable evidence at the appropriate proof levels;
4. explain migration, compatibility, and operational impact;
5. add an ADR when the enforcement strategy changes materially.

Coverage percentage alone is not evidence that an invariant holds.

The normative transition table is [PAYMENT_STATE_MACHINE.md](PAYMENT_STATE_MACHINE.md), ledger examples are [LEDGER_POSTINGS.md](LEDGER_POSTINGS.md), and retry/error semantics are [IDEMPOTENCY_AND_ERRORS.md](IDEMPOTENCY_AND_ERRORS.md).
