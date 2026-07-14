# Payment State Machine

This document is the executable transition contract for the MVP. Implementations may refactor internal types, but they must preserve these observable rules or supersede them through an ADR.

## State and monetary facts

`PaymentIntent` owns these monetary facts in BRL minor units:

- `requestedAmount` — immutable amount requested by the merchant;
- `authorizedAmount` — amount proven authorized by the PSP;
- `capturedAmount` — sum of successful captures;
- `refundedAmount` — sum of successful refunds;
- `version` — optimistic concurrency token.

The available amounts are derived:

```text
remainingCapturable = authorizedAmount - capturedAmount
remainingRefundable = capturedAmount - refundedAmount
```

## States

| State | Meaning | Allowed next business action |
| --- | --- | --- |
| `CREATED` | Intent exists but authorization has not started | Authorize or cancel |
| `AUTHORIZATION_PENDING` | Local intent committed; provider call may be in flight | Provider outcome only |
| `AUTHORIZATION_UNKNOWN` | Final provider outcome is not yet proven | Status lookup, callback, or reconciliation |
| `AUTHORIZED` | Provider approved and no capture succeeded | Capture |
| `PARTIALLY_CAPTURED` | Positive captured amount remains below authorization | Capture or begin refund |
| `CAPTURED` | Captured amount equals authorization | Begin refund |
| `PARTIALLY_REFUNDED` | Positive refund remains below captured amount | Refund only |
| `REFUNDED` | Refunded amount equals captured amount | No monetary command |
| `DECLINED` | Provider issued a conclusive business decline | No command in the MVP |
| `FAILED` | A conclusive non-financial terminal failure occurred and provider non-processing is proven | No command in the MVP |
| `CANCELLED` | Merchant cancelled before authorization | No command |

The MVP deliberately forbids new captures after the first successful refund. This avoids interleaving settlement directions in one aggregate state. Supporting interleaved capture/refund requires a new ADR and state-model revision.

## Transition table

| Command or evidence | Source | Guard | Target | Ledger effect |
| --- | --- | --- | --- | --- |
| Create intent | — | Valid positive BRL amount | `CREATED` | None |
| Cancel | `CREATED` | Authenticated owner; idempotent command | `CANCELLED` | None |
| Begin authorization | `CREATED` | Valid idempotency record acquired | `AUTHORIZATION_PENDING` | None |
| Provider approval | `AUTHORIZATION_PENDING`, `AUTHORIZATION_UNKNOWN` | Provider reference and approved amount are valid | `AUTHORIZED` | None |
| Provider decline | `AUTHORIZATION_PENDING`, `AUTHORIZATION_UNKNOWN` | Conclusive provider business evidence | `DECLINED` | None |
| Provider outcome unavailable | `AUTHORIZATION_PENDING` | Timeout, lost response, or inconclusive protocol result | `AUTHORIZATION_UNKNOWN` | None |
| Proven non-processing failure | `AUTHORIZATION_PENDING`, `AUTHORIZATION_UNKNOWN` | Evidence proves the provider created no authorization and recovery is impossible | `FAILED` | None |
| Partial capture | `AUTHORIZED`, `PARTIALLY_CAPTURED` | `0 < amount < remainingCapturable` | `PARTIALLY_CAPTURED` | Balanced capture transaction |
| Final capture | `AUTHORIZED`, `PARTIALLY_CAPTURED` | `amount == remainingCapturable` | `CAPTURED` | Balanced capture transaction |
| Partial refund | `PARTIALLY_CAPTURED`, `CAPTURED`, `PARTIALLY_REFUNDED` | `0 < amount < remainingRefundable` and no capture follows a refund | `PARTIALLY_REFUNDED` | Balanced compensating transaction |
| Final refund | `PARTIALLY_CAPTURED`, `CAPTURED`, `PARTIALLY_REFUNDED` | `amount == remainingRefundable` | `REFUNDED` | Balanced compensating transaction |

Every accepted command increments `version`, appends timeline/audit evidence, and completes its persistent idempotency outcome in the same local transaction as its business effect.

## Provider recovery rules

| Evidence while unknown | Result |
| --- | --- |
| Status lookup proves approval | Transition to `AUTHORIZED` once |
| Callback proves approval | Transition to `AUTHORIZED` once |
| Status lookup/callback proves decline | Transition to `DECLINED` once |
| Provider still cannot determine the outcome | Remain `AUTHORIZATION_UNKNOWN`; record the attempt |
| Evidence conflicts with internal state | Open or update one reconciliation case |
| Authorized at provider but local effect is missing | Apply the guarded missing local effect and preserve reconciliation evidence |

Recovery is evidence-driven. A timeout, retry exhaustion, process restart, or operator opinion alone cannot choose approval or decline.

## Stable rejection behavior

| Condition | Error code | HTTP status |
| --- | --- | --- |
| Command is illegal in the current state | `INVALID_PAYMENT_TRANSITION` | `409` |
| Capture exceeds `remainingCapturable` | `CAPTURE_LIMIT_EXCEEDED` | `409` |
| Refund exceeds `remainingRefundable` | `REFUND_LIMIT_EXCEEDED` | `409` |
| Capture attempted after any successful refund | `CAPTURE_AFTER_REFUND_NOT_SUPPORTED` | `409` |
| Stale aggregate version loses a race | `CONCURRENT_PAYMENT_MODIFICATION` | `409` |
| Resource is absent or belongs to another merchant | `PAYMENT_INTENT_NOT_FOUND` | `404` |

Rejected transitions do not post ledger entries. They may record safe technical telemetry, but they do not create misleading business audit effects.

## Worked lifecycle

```text
CREATED
  -> AUTHORIZATION_PENDING
  -> AUTHORIZATION_UNKNOWN
  -> AUTHORIZED
  -> PARTIALLY_CAPTURED
  -> CAPTURED
  -> PARTIALLY_REFUNDED
  -> REFUNDED
```

The timeline retains every intermediate attempt and evidence item even though the current resource exposes only the latest state.

## Required tests

- a parameterized test for every accepted row;
- a parameterized test for every state/command pair not accepted by the table;
- timeout-before-processing and timeout-after-processing recovery;
- duplicate and out-of-order provider evidence;
- capture/refund boundaries at zero, one minor unit, exact remainder, and remainder plus one;
- optimistic version races against real PostgreSQL;
- audit, idempotency, and ledger side-effect assertions for every monetary transition.
