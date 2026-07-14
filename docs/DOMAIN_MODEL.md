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

The BRL-only MVP uses integer minor units:

```java
public record Money(long amountInMinorUnits, Currency currency) {}
```

The constructor must reject negative amounts where the operation requires a positive value. Currency-specific scale and a possible move to `BigDecimal` will be reconsidered before multi-currency support.

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

The exact chart of accounts and debit/credit direction must be documented with worked examples before ledger implementation begins.
