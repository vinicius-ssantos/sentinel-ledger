# Persistent Idempotency and Error Contract

Every mutating merchant command requires an `Idempotency-Key`. PostgreSQL, not process memory or Redis, is authoritative for acquisition, request identity, outcome, and recovery.

## Key scope and format

The unique scope is:

```text
(merchantId, operationName, idempotencyKey)
```

Examples of `operationName` are `payment-intent.create`, `payment-intent.authorize`, `capture.create`, `refund.create`, and `payment-intent.cancel`.

The key is case-sensitive, contains 16–128 visible ASCII characters, and should be generated with sufficient entropy. It is not a credential and must not contain personal or payment data.

## Canonical request identity

The request hash is SHA-256 over UTF-8 bytes of:

```text
operationName + "\n" + merchantId + "\n" + RFC8785-canonical-command-json
```

The canonical command contains only business input after syntactic validation. It includes resource identifiers and monetary amounts, but excludes the idempotency key, authorization credential, correlation ID, transport timestamp, and other volatile headers.

Equivalent JSON member ordering produces the same hash. A different amount, resource, currency, or semantic field produces a different hash.

## Record lifecycle

| State | Meaning | Replay behavior |
| --- | --- | --- |
| `IN_PROGRESS` | One request owns a bounded processing lease | Return `409 IDEMPOTENCY_REQUEST_IN_PROGRESS` with `Retry-After` |
| `COMPLETED` | A committed terminal response is stored | Replay original status and safe body |
| `RECOVERY_REQUIRED` | A durable business state exists but final response/effect needs evidence-based recovery | Return `202` with resource location and recovery status |
| `FAILED_TERMINAL` | A deterministic business rejection after acquisition is stored | Replay the same problem response |

An expired processing lease does not authorize blind repetition of an external PSP call. Recovery first inspects the durable payment state and provider evidence.

## Acquisition workflow

1. Authenticate the principal and resolve `merchantId`.
2. Validate key syntax and request shape.
3. Build the canonical business command and hash.
4. Attempt to insert the uniquely scoped `IN_PROGRESS` record.
5. If the record exists, lock/read it and compare hashes.
6. Execute only when the caller owns the acquisition or a guarded recovery path.
7. Commit business state, ledger/audit effects, and the terminal idempotency outcome atomically where they share a local transaction.

For the persist-call-persist authorization workflow, the record points to the durable payment and may temporarily remain `IN_PROGRESS` or become `RECOVERY_REQUIRED` across the provider call. A restart resumes from evidence rather than creating a second authorization.

## Duplicate behavior

### Same key and same request

- `COMPLETED` or `FAILED_TERMINAL`: return the stored status and safe response body;
- add `Idempotent-Replayed: true`;
- preserve original resource identity and `Location`;
- generate a new transport correlation ID while linking it to the original operation;
- do not repeat payment, provider, ledger, outbox, webhook, or audit business effects.

### Same key and different request

Return `409 IDEMPOTENCY_KEY_REUSED` without revealing the original request body. The response may expose the operation name and original creation time but not the stored hash.

### Concurrent acquisition

Exactly one transaction inserts/owns the record. Other callers observe `IN_PROGRESS` or the committed outcome. Waiting on an application-local mutex is not a correctness mechanism.

## Which responses are stored

- successful committed responses;
- deterministic domain conflicts produced after acquisition;
- conclusive provider declines;
- accepted recovery responses tied to durable resource state;
- safe response headers: `Content-Type`, `Location`, and API version when applicable.

Authentication failures, malformed JSON, missing/invalid keys, and basic validation failures occur before acquisition and are not stored. An unexpected failure before any effect releases or expires the lease through a guarded recovery policy. An unexpected failure after a possible effect becomes `RECOVERY_REQUIRED`.

## Retention

- complete replayable response: minimum 7 days;
- tombstone containing scope, request hash, resource identity, and terminal state: minimum 30 days;
- ledger and audit evidence follow their own longer-lived immutable policy;
- cleanup is a bounded auditable job and never deletes an active/in-progress record;
- reusing a key after the documented tombstone window is treated as a new request.

The durations are MVP policy, not a production recommendation. Changing them requires an ADR and migration plan.

## RFC 9457 problem details

```json
{
  "type": "https://sentinel-ledger.dev/problems/idempotency-key-reused",
  "title": "Idempotency key cannot be reused for a different request",
  "status": 409,
  "detail": "Use a new key for a request with different business parameters.",
  "instance": "/api/v1/payment-intents/pay_001/captures",
  "code": "IDEMPOTENCY_KEY_REUSED",
  "correlationId": "01J...",
  "violations": []
}
```

`detail` is safe for clients and never contains a stack trace, SQL, secret, hash, provider payload, or another merchant's identifier.

## Stable error catalog

| HTTP | Code | Meaning |
| ---: | --- | --- |
| `400` | `INVALID_REQUEST` | Malformed or semantically invalid request |
| `400` | `IDEMPOTENCY_KEY_REQUIRED` | Mutating command omitted the key |
| `400` | `IDEMPOTENCY_KEY_INVALID` | Key violates format rules |
| `401` | `AUTHENTICATION_REQUIRED` | No valid authenticated principal |
| `403` | `OPERATOR_ACTION_FORBIDDEN` | Authenticated actor lacks an operator permission |
| `404` | `PAYMENT_INTENT_NOT_FOUND` | Resource absent or hidden by merchant ownership |
| `409` | `IDEMPOTENCY_KEY_REUSED` | Same scope/key with a different canonical request |
| `409` | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | Original request still owns the processing lease |
| `409` | `INVALID_PAYMENT_TRANSITION` | Command is illegal in the current state |
| `409` | `CAPTURE_LIMIT_EXCEEDED` | Requested capture exceeds remaining authorization |
| `409` | `REFUND_LIMIT_EXCEEDED` | Requested refund exceeds remaining captured value |
| `409` | `CONCURRENT_PAYMENT_MODIFICATION` | Caller lost a valid state/version race |
| `422` | `UNSUPPORTED_CURRENCY` | MVP accepts BRL only |
| `202` | `PROVIDER_OUTCOME_UNKNOWN` | Durable operation requires evidence-based recovery |
| `502` | `PROVIDER_PROTOCOL_ERROR` | Provider response is conclusively invalid and safe to expose as a gateway error |
| `503` | `DEPENDENCY_TEMPORARILY_UNAVAILABLE` | Temporary dependency failure with no ambiguous business effect |

`202 PROVIDER_OUTCOME_UNKNOWN` uses the normal accepted-resource representation plus `Location`; it is listed here for contract completeness but is not an RFC 9457 error response.

## Required contract tests

- missing and invalid key;
- same key/same request replay after restart;
- same key/different amount conflict;
- same key/different resource conflict;
- concurrent acquisition with one owner;
- in-progress response and `Retry-After`;
- timeout-after-processing transitions to recovery rather than blind retry;
- terminal business failure replay;
- safe response-header replay;
- retention/tombstone cleanup boundaries;
- problem schema, stable code, ownership hiding, and redaction assertions.
