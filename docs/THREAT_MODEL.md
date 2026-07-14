# Threat Model

This is a lightweight engineering threat model for an educational system. It is not a PCI DSS assessment, compliance claim, or authorization to process real payments.

## Protected assets

- payment state and monetary limits;
- ledger integrity and history;
- idempotency records and stored outcomes;
- simulated PSP and webhook secrets;
- merchant/operator identity;
- reconciliation and audit evidence;
- logs, traces, metrics, and demo data.

## Trust boundaries

1. Merchant client to public API.
2. Operator UI to privileged operator API.
3. Application to PostgreSQL.
4. Application to simulated PSP.
5. Outbox/worker to broker and webhook receiver when those phases exist.
6. Application to telemetry backends.

## Priority threats and controls

| Threat | Required control | Verification |
| --- | --- | --- |
| Merchant accesses another resource | Identity from authenticated principal plus ownership checks | Negative API authorization tests |
| Forged operator correction | Separate role, explicit reason, confirmation, and audit evidence | Security and audit integration tests |
| Idempotency key reused with a modified request | Canonical request hash and conflict response | Contract and concurrency tests |
| Concurrent capture/refund exceeds limits | Database-backed concurrency control | Real PostgreSQL race tests |
| Ledger history is modified | No public mutation path, append-only repositories, restricted database permissions | Integration/security tests |
| Forged or replayed webhook | HMAC signature, timestamp window, event identity, and secret rotation | Invalid, expired, and duplicate examples |
| Secret or personal data leaks into telemetry | Allowlisted structured fields and redaction | Automated log/fixture assertions |
| Unbounded retries exhaust resources | Maximum attempts, backoff, DLQ/operator state | Failure-mode tests and metrics |
| Reconciliation action hides evidence | Append-only resolution history and compensating entries | Case history verification |
| Dependency or build compromise | Pinned build tooling, dependency review, secret scanning, SBOM before release | CI checks |

## Data minimization

The project must never collect or store PAN, CVV, real card tokens, real PSP credentials, or production customer data. Fixtures and screenshots use synthetic values only.

## Security acceptance bar

Before a public demonstration:

- authentication and ownership tests pass;
- operator actions are authorized and audited;
- webhook verification examples cover invalid, expired, and replayed requests;
- telemetry contains no configured secret or forbidden payment field;
- dependency, secret, and container scanning results are available;
- known limitations are documented without claiming production compliance.
