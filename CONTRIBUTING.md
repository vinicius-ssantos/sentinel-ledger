# Contributing

Sentinel Ledger is currently a personal portfolio project. Focused discussions, issue reports, and pull requests are welcome when they preserve the project's scope and evidence-driven architecture.

## Before opening a change

1. Search existing issues and ADRs.
2. Open or reference an issue for non-trivial behavior.
3. Explain the business invariant or failure mode being addressed.
4. Avoid adding infrastructure without a measured requirement and ADR.
5. Use the stable IDs in `docs/INVARIANTS.md` when correctness behavior is affected.

## Pull request expectations

- use a focused branch and conventional commit messages;
- include tests for changed behavior;
- update OpenAPI and documentation when contracts change;
- preserve module boundaries;
- include migration and rollback notes for persistence changes;
- state security, concurrency, and observability impact;
- include recovery/restart evidence when a change crosses a network or transaction boundary;
- update the golden demo when reviewer-visible behavior changes;
- avoid unrelated formatting or dependency changes.

## Definition of done

A change is ready when its acceptance criteria pass, relevant invariant IDs have executable evidence at the required proof levels, module verification is green, documentation and the golden demo match behavior, secrets and real payment data are absent, and the PR describes trade-offs honestly.

## Commit examples

```text
feat(payments): create payment intent aggregate
fix(idempotency): reject key reuse with different payload
test(ledger): prove balanced transaction invariant
docs(adr): defer RabbitMQ until outbox phase
```

## Local setup

Local build commands will be documented after the Spring Boot bootstrap issue lands. Until then, this repository is intentionally documentation-first.

Validate the current repository documentation with:

```bash
python3 scripts/validate_docs.py
```
