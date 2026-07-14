# Golden Demo Runbook

Status: **demonstration contract; commands will become executable as implementation phases land**.

The golden demo is the portfolio acceptance test. It must prove correctness and recovery, not merely show successful CRUD requests.

## Target experience

A reviewer should understand the problem in under one minute and complete the six scenarios in approximately five to ten minutes using one documented startup command, synthetic seed data, and deterministic PSP controls.

## Required scenarios

| # | Demonstration | Expected proof |
| --- | --- | --- |
| 1 | Submit one mutating request three times with the same key and payload | One business effect and byte-equivalent replayable outcome |
| 2 | Reuse the same key with a modified amount | Stable conflict response and no second effect |
| 3 | Race at least twenty captures against one authorization | Successful total never exceeds the authorization; losing requests are deterministic |
| 4 | Make the PSP process an authorization and lose the response | Local state becomes `AUTHORIZATION_UNKNOWN`; status lookup or reconciliation reaches the proven final state without duplication |
| 5 | Execute a partial refund | A new balanced compensating ledger transaction appears; prior entries remain unchanged |
| 6 | Introduce a provider/internal mismatch and restart the process | One reconciliation case survives restart, preserves evidence, and records an audited resolution |

## Evidence visible for every scenario

- API response and stable problem detail when applicable;
- payment state and version;
- idempotency record identity;
- provider attempt and external reference;
- payment timeline;
- ledger transaction and entries when money moved;
- reconciliation case when evidence diverged;
- correlation identifier, trace, and relevant business metric;
- audit actor and reason for operator action.

## Reproducibility requirements

- exact JDK, container runtime, database, and resource limits;
- one startup command and one reset command;
- synthetic deterministic seed;
- no real credentials or payment data;
- commands committed with the repository;
- expected output or assertions for each step;
- final invariant verification after the demo;
- known environment and performance limitations.

## Completion gate

The project is portfolio-ready only when a clean checkout can run this demonstration and the automated test suite verifies the same invariants independently of the UI.
