# Failure Model

Sentinel Ledger treats network and provider failures as domain inputs. The simulated PSP must make these outcomes deterministic enough for automated tests and demonstrations.

## Persist-call-persist rule

Authorization never keeps a PostgreSQL transaction open while waiting for the PSP:

1. persist `AUTHORIZATION_PENDING` and commit;
2. call the provider outside the database transaction;
3. persist `AUTHORIZED`, `DECLINED`, or `AUTHORIZATION_UNKNOWN`;
4. recover uncertainty through status lookup, callback, or reconciliation.

The system must not infer a final outcome from a transport exception.

## Provider scenarios

| Scenario | Provider behavior | Expected system behavior | Evidence |
| --- | --- | --- | --- |
| Approval | Returns an approved authorization | Persist `AUTHORIZED` once | Timeline and provider reference |
| Decline | Returns a business rejection | Persist `DECLINED` without ledger posting | Stable reason code |
| Timeout before processing | Performs no operation and times out | Persist `AUTHORIZATION_UNKNOWN`, then recover to a final state | Status lookup evidence |
| Timeout after processing | Approves internally but the response is lost | Remain uncertain until lookup/callback proves approval | No duplicate authorization |
| Temporary protocol failure | Returns a retryable technical error | Bounded retry or explicit recoverable failure | Attempt metrics |
| Permanent protocol failure | Returns an invalid/non-retryable response | Stop retrying and expose safe operational evidence | Redacted diagnostic |
| Duplicate callback | Sends the same event repeatedly | Apply the business effect once | Duplicate counter and one transition |
| Out-of-order callback | Delivers older evidence after newer evidence | Reject stale transition without corrupting history | Audit/timeline record |
| Delayed callback | Sends final evidence after local timeout | Reconcile safely with the current state | Final state and case history |
| Provider/internal mismatch | Status differs from local evidence | Open one deduplicated reconciliation case | Case fingerprint and severity |

## Simulation controls

Failure selection belongs to a test/demo-only control surface, not to the public merchant API. A deterministic scenario identifier may configure latency, processing point, response, callback count, callback delay, and callback ordering.

Every scenario must be usable in Testcontainers-backed integration tests and in the public demo runbook.

## Retry policy

- retries are bounded and observable;
- idempotency identity is preserved across retries;
- business declines are not retried as technical failures;
- unknown outcomes are recovered through evidence, not blind repetition;
- no infinite retry loop is allowed;
- retry exhaustion produces an operator-visible state.
