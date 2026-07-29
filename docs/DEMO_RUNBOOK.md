# Golden Demo Runbook

Status: **the core financial golden path is executable; advanced failure scenarios remain explicitly pending external automation**.

The golden demo is the portfolio acceptance test. It must prove correctness and recovery, not merely show successful CRUD requests.

## Executable core golden path

From a clean checkout on Linux, macOS, or WSL, with JDK 25, Docker, `curl`, and `jq` available:

```bash
bash scripts/golden-demo.sh --reset
```

The command:

1. removes the local Compose volumes when `--reset` is supplied;
2. starts PostgreSQL;
3. starts Sentinel Ledger when no healthy instance is already running;
4. waits for `/actuator/health` to report `UP`;
5. creates a synthetic BRL 100.00 payment intent;
6. replays the same request with the same idempotency key and requires a byte-equivalent stored response;
7. reuses that key with a different amount and requires `IDEMPOTENCY_KEY_REUSED`;
8. authorizes through the deterministic simulated PSP;
9. captures BRL 100.00;
10. posts a BRL 30.00 partial compensating refund;
11. verifies the final authoritative amounts and state;
12. requires audit, provider, and ledger evidence on the payment timeline.

Every assertion fails the process with a non-zero exit code. The script uses only the development credentials from `application.properties` and synthetic payment data. When it starts the Spring Boot process, it also stops that process on exit. PostgreSQL is left running so the generated evidence can be inspected; reset it with:

```bash
docker compose down --volumes
```

If a healthy application already exists at `http://localhost:8080`, the script reuses it and never stops it. In that mode, omit `--reset`. Override the target or credentials with:

- `SENTINEL_DEMO_BASE_URL`;
- `SENTINEL_MERCHANT_API_KEY_ID`;
- `SENTINEL_MERCHANT_API_KEY_SECRET`.

## Scenario coverage

| # | Demonstration | Current executable proof |
| --- | --- | --- |
| 1 | Submit one mutating request three times with the same key and payload | **Partially automated:** the script submits the create request twice, requires `Idempotent-Replayed: true`, and compares the stored response byte-for-byte; repeated replay is independently covered by integration tests. |
| 2 | Reuse the same key with a modified amount | **Automated:** stable `409` with `IDEMPOTENCY_KEY_REUSED` and no second effect. |
| 3 | Race at least twenty captures against one authorization | **Pending external demo automation:** the concurrency invariant is implemented and covered by the integration suite, but no committed k6/CLI runner is published yet. |
| 4 | Make the PSP process an authorization and lose the response | **Pending external demo automation:** deterministic timeout-after-processing recovery is covered by integration tests; public controls are not exposed through the production API. |
| 5 | Execute a partial refund | **Automated:** a full capture followed by a partial refund reaches `PARTIALLY_REFUNDED`, preserves captured value, and surfaces compensating ledger evidence. |
| 6 | Introduce a provider/internal mismatch and restart the process | **Pending external demo automation:** reconciliation durability and resolution are covered by integration tests, but the clean-checkout runner is not committed yet. |

The table deliberately distinguishes executable reviewer evidence from internal automated-test evidence. A scenario is not called demo-ready until a clean checkout can run it through a documented external command.

## Evidence required by the core command

- API response and stable problem detail when applicable;
- payment state and monetary totals;
- idempotent replay header and byte-equivalent response;
- provider authorization result;
- payment timeline;
- capture and refund ledger transactions;
- audit events for create, authorize, capture, and refund.

## Reproducibility contract

- JDK version is pinned by `.java-version`;
- PostgreSQL is provided by `compose.yaml`;
- local credentials are development-only and overrideable through environment variables;
- all payment data is synthetic and BRL-only;
- the script contains assertions, not just printed requests;
- CI validates the shell syntax and help path;
- `./mvnw verify` independently proves domain, persistence, concurrency, and recovery invariants;
- no latency or throughput claim is produced by this demo.

## Remaining completion gate

The full six-scenario portfolio demonstration will be complete only when committed external runners also prove:

- at least twenty concurrent captures with a final invariant check;
- timeout-after-processing recovery through deterministic PSP controls;
- durable mismatch detection, restart, and audited reconciliation resolution.

Until those runners exist, the repository describes the current command as the **core golden path**, not the complete Phase 4 demonstration.
