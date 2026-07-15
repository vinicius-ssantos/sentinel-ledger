# Domain Model

## Bounded concerns

The payment lifecycle and the ledger are related but distinct. Payments decide whether an operation is allowed. The ledger records the financial effect of completed operations.

## Core concepts

### Merchant

Represents the API client that owns payment intents and idempotency keys. The MVP contains one configured merchant but retains the identifier in persistence constraints.

### PaymentIntent

Aggregate root for the intended amount and payment lifecycle. It owns authorization, capture, and refund limits.

Initial states:

```text
CREATED
AUTHORIZATION_PENDING
AUTHORIZATION_UNKNOWN
AUTHORIZED
PARTIALLY_CAPTURED
CAPTURED
PARTIALLY_REFUNDED
REFUNDED
DECLINED
FAILED
CANCELLED
```

`AUTHORIZATION_UNKNOWN` is mandatory: it represents missing final evidence after a provider call, not a guessed failure. Recovery may transition it to `AUTHORIZED`, `DECLINED`, or a documented operator-visible terminal outcome.

The normative command, guard, and transition table is maintained in [PAYMENT_STATE_MACHINE.md](PAYMENT_STATE_MACHINE.md). The MVP forbids new capture after the first successful refund; this avoids an ambiguous interleaving of capture and refund phases in one aggregate state.

### Authorization

Represents the PSP decision and the amount available for capture. Authorization does not automatically represent a settled movement of funds.

### Capture

Represents a successful conversion of authorized value into a captured amount. A successful capture posts a balanced ledger transaction.

### Refund

Represents returned value bounded by the net captured amount. A successful refund posts a compensating ledger transaction; it does not modify prior entries.

### LedgerTransaction

An immutable group of two or more entries that must balance before persistence.

### LedgerEntry

An append-only debit or credit in minor currency units. Entries are never updated or deleted.

### IdempotencyRecord

Uniquely identified by merchant, operation, and key. Stores a canonical request hash, processing state, resource identifier, response status, response payload, and expiry metadata.

### ReconciliationCase

Represents a detected mismatch between internal state and the simulated PSP. Resolution records an actor, reason, and action without erasing original evidence.

Initial lifecycle:

```text
OPEN
INVESTIGATING
RESOLVED
IGNORED_WITH_REASON
```

A deterministic mismatch fingerprint prevents repeated runs from creating duplicate open cases.

### AuditEvent

Records who performed a sensitive action, what changed, when it happened, which resource was affected, and why.

## Money representation

The `money` module owns an immutable `Money` value object backed by a signed `long` in minor units and a `Currency` value containing an uppercase three-letter code plus explicit fraction digits. No monetary path uses `double` or `float`.

```java
Money amount = Money.positive(12_50, Currency.BRL); // BRL 12.50
Money delta = Money.ofMinor(-2_00, Currency.BRL);   // signed ledger/reconciliation delta
```

The base value object accepts signed amounts because ledger calculations, differences, and compensating effects may be negative. Operation boundaries select the required factory explicitly:

- `positive(...)` rejects zero and negative amounts;
- `nonNegative(...)` accepts zero but rejects negative amounts;
- `zero(...)` expresses an intentional zero;
- `ofMinor(...)` accepts a signed value for calculations and evidence.

Addition, subtraction, negation, and comparison require equal currencies. A mismatch throws `CurrencyMismatchException`. Arithmetic delegates to `Math.addExact`, `Math.subtractExact`, and `Math.negateExact`, so overflow fails instead of wrapping silently.

The product MVP accepts BRL at payment API boundaries. The value object can represent another validated currency code so cross-currency operations remain detectable and testable; this does not make the MVP multi-currency.

### API boundary contract

API DTOs must not expose the domain record directly. Monetary values use a decimal string containing integer minor units plus the ISO-style currency code:

```json
{
  "amountInMinorUnits": "1250",
  "currency": "BRL"
}
```

The string form avoids precision loss in clients whose numeric type cannot represent every Java `long`. Decimal major-unit strings such as `"12.50"` are not mixed into this contract.

### Persistence boundary contract

PostgreSQL stores the amount in a `BIGINT` column and the currency code in a constrained three-character column. Fraction digits are validated by the domain currency definition; they are not inferred from a floating-point or database decimal scale. Repositories reconstruct `Money` explicitly and never persist it as `DOUBLE PRECISION`, `REAL`, or an ORM-specific serialized object.

## Mandatory invariants

- capture total is less than or equal to authorized amount;
- refund total is less than or equal to net captured amount;
- ledger debit total equals ledger credit total;
- all entries in a ledger transaction use the same currency;
- a posted ledger transaction is immutable;
- idempotency key reuse with a different canonical request is rejected;
- duplicate provider callbacks do not apply effects twice;
- uncertain provider outcomes remain explicit until evidence resolves them;
- repeated detection does not duplicate an open reconciliation case;
- resolution preserves evidence, actor, reason, and resulting compensating action;
- the balance projection can be rebuilt from authoritative ledger entries;
- only allowed state transitions succeed.

Stable invariant identifiers and required proof levels are defined in [INVARIANTS.md](INVARIANTS.md).

## Initial ledger policy

Authorization creates payment state and may create a hold representation, but capture is the first operation required to post the MVP's financial ledger entries. Refund posts a new compensating transaction.

Ledger implementation must follow the accepted MVP chart and worked postings in [LEDGER_POSTINGS.md](LEDGER_POSTINGS.md). Persistent command identity and error behavior are defined in [IDEMPOTENCY_AND_ERRORS.md](IDEMPOTENCY_AND_ERRORS.md).
