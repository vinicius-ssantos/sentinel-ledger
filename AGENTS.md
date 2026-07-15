# Repository instructions

## Purpose and scope

This file is the durable, repository-wide instruction source for coding agents working on Sentinel Ledger. Keep it concise, actionable, and aligned with the executable system. More specific instructions may be added beside a future module only when that module has stable conventions that genuinely differ from these rules.

Sentinel Ledger is in Phase 1 and has an executable Java 25 and Spring Boot 4.1 foundation. Do not claim that payment workflows, persistence, module enforcement, deployment, or benchmark evidence exists until each is implemented and reproducible.

## Source of truth

Use this order when making decisions:

1. accepted ADRs in `docs/adr/` explain architectural decisions and trade-offs;
2. normative domain documents define behavior: `docs/INVARIANTS.md`, `docs/PAYMENT_STATE_MACHINE.md`, `docs/LEDGER_POSTINGS.md`, `docs/IDEMPOTENCY_AND_ERRORS.md`, and `docs/FAILURE_MODEL.md`;
3. the issue and pull-request acceptance criteria define the scope of the current change;
4. once implementation exists, OpenAPI, migrations, code, and executable tests must remain consistent with those contracts.

`README.md` is the project overview, not a substitute for the normative contracts. If sources disagree, stop and reconcile them explicitly; do not silently choose one or invent behavior.

## Read before changing behavior

Start with the issue and only the documents relevant to the change. Always inspect `docs/INVARIANTS.md` when financial, state, retry, reconciliation, audit, or concurrency behavior is affected. Read the corresponding ADR before changing an established architectural decision.

For changes that cross modules, transaction boundaries, or invariant IDs, describe a short plan before editing. A typo or isolated wording correction does not require a formal plan.

## Architectural boundaries

- Preserve the Spring Boot modular monolith and the dependency policy in `docs/ARCHITECTURE.md`.
- Keep `payments`, `ledger`, `reconciliation`, `idempotency`, `integration.psp`, `merchant`, `audit`, and `observability` responsibilities distinct.
- Depend on another module through its public API. Do not access another module's internal packages or introduce cycles.
- Keep provider-specific types inside `integration.psp`; expose provider-neutral outcomes to the payment domain.
- PostgreSQL is authoritative for payment state, idempotency, ledger entries, audit evidence, and reconciliation cases.
- Do not add Redis, a broker, microservices, Kubernetes, native images, or authoritative balance caches without a measured requirement and an accepted ADR.

## Non-negotiable correctness rules

- Represent BRL money as integer minor units through a dedicated value type. Never use binary floating-point for money.
- Preserve every invariant ID in `docs/INVARIANTS.md`; changed enforcement requires matching executable evidence.
- Never hold a database transaction open across a PSP network call.
- Represent transport uncertainty explicitly. A timeout is not proof of provider success or failure.
- Enforce idempotency in PostgreSQL with the documented canonical request hash, lifecycle, replay, conflict, and retention semantics.
- Never capture more than the authorized amount or refund more than the available captured amount.
- The MVP accepts no new capture after its first successful refund.
- Post ledger effects as complete, balanced transactions. Posted entries are append-only; corrections use compensating transactions.
- Duplicate or out-of-order provider evidence must not apply an effect twice.
- Reconciliation must deduplicate open mismatches and preserve original evidence, actor, reason, and resolution history.

## Implementation conventions

- Use English for public documentation, code identifiers, API contracts, commit messages, and pull requests.
- Prefer explicit domain types and outcomes over primitives, flags, null-driven control flow, or provider leakage.
- Use records for immutable boundary values when they improve clarity; use sealed hierarchies only for genuinely closed outcome sets.
- Keep transaction boundaries visible in application services and small enough to reason about.
- Treat database constraints as part of correctness, not merely persistence detail.
- Return stable RFC 9457 problem details for documented API failures.
- Do not introduce a dependency or abstraction without a present use case.
- Keep changes focused. Avoid unrelated formatting, renaming, dependency, or infrastructure churn.

## Testing and verification

Every behavior change must prove the affected invariant at the levels required by `docs/INVARIANTS.md`.

- Use Testcontainers with PostgreSQL for persistence, locking, isolation, migration, and restart claims; do not substitute H2 for those proofs.
- Exercise concurrency with independent real database transactions.
- Use deterministic simulated-PSP scenarios for timeout-before-processing, timeout-after-processing, retryable and permanent failures, delayed callbacks, duplicates, reordering, and mismatches.
- Assert final monetary, state, ledger, and idempotency invariants after load or concurrency tests. Throughput alone is not a valid result.
- Test both accepted and rejected state transitions.
- Trace important tests to stable invariant IDs in names, display names, or nearby documentation.
- Run the narrowest relevant checks while iterating, then the complete verification command before handoff.

Run the complete application verification before handoff:

```bash
./mvnw verify
```

Also verify documentation and repository instruction contracts with:

```bash
git diff --check
python3 scripts/validate_docs.py
```

The validator checks trailing whitespace, relative Markdown links, the `CLAUDE.md` import, the ignored local Claude file, and the agent-instruction size limit.

## Contract and documentation changes

When behavior or an invariant changes, update all affected artifacts in the same pull request:

- the stable invariant entry and proof requirement;
- the relevant state-machine, ledger, idempotency, failure, threat, or architecture document;
- an ADR when a material decision or enforcement strategy changes;
- OpenAPI and migration notes once those artifacts exist;
- the golden demo when reviewer-visible behavior changes.

Keep Markdown links relative and resolvable. Distinguish planned work from implemented and measured results. Do not publish unsupported performance, scale, security, compliance, or production-readiness claims.

## Security and data handling

- Use synthetic data only. Never store or commit PAN, CVV, real card tokens, payment credentials, secrets, or production data.
- Derive merchant identity from authenticated context and enforce ownership at resource boundaries; never trust a merchant header by itself.
- Redact credentials, sensitive payloads, and personal data from logs, metrics, traces, fixtures, examples, and audit evidence.
- Update `docs/THREAT_MODEL.md` when a change adds a trust boundary, privileged operation, callback surface, or sensitive data flow.
- Follow `SECURITY.md` for vulnerability reporting. Do not disclose a suspected vulnerability in a public issue.

## Git and pull-request discipline

- Work on a focused non-protected branch; never write directly to `main`.
- Preserve existing user changes and avoid destructive Git operations.
- Use conventional commits such as `feat(payments): ...`, `fix(idempotency): ...`, or `docs(adr): ...`.
- Reference the issue and affected invariant IDs in the pull request.
- Report verification evidence and any checks that could not be run.
- Do not merge, close, release, deploy, or publish unless the owner explicitly asks for that action.
- Do not add an open-source license until the owner makes that decision.

## Maintaining these instructions

Keep this file small enough to load reliably. Add only durable rules that prevent recurring mistakes or materially improve repository work, and pair critical requirements with tests, constraints, linters, or hooks. Use a nearer `AGENTS.md` for stable directory-specific guidance and `AGENTS.override.md` only for a temporary, intentional override. Remove obsolete instructions when the repository evolves.
