# MVP Ledger Policy and Posting Examples

The ledger records completed financial effects. Payment state decides whether an operation is allowed; the ledger explains the resulting movement without editing history.

All examples use BRL integer minor units. `10000` means **R$100.00**.

## Chart of accounts

| Account pattern | Type | Normal balance | Meaning |
| --- | --- | --- | --- |
| `psp-clearing-receivable:BRL` | Asset | Debit | Amount the simulated PSP owes the platform after capture |
| `merchant-payable:{merchantId}:BRL` | Liability | Credit | Amount the platform owes the merchant after capture |

The single-merchant MVP still stores `merchantId` in account identity and uniqueness constraints. Fees, reserves, disputes, chargebacks, bank settlement, and multiple currencies are outside this chart of accounts.

## Posting rules

- a `LedgerTransaction` contains at least two entries;
- every entry amount is a positive integer in minor units;
- direction is represented by `DEBIT` or `CREDIT`, never by a negative amount;
- all entries in one transaction use the same currency;
- total debits equal total credits before persistence;
- transaction and entries are inserted atomically;
- a posted transaction is immutable;
- the same business effect cannot post twice;
- correction creates a linked compensating transaction;
- balance projections are disposable and rebuildable.

## Authorization

Authorization changes payment availability but does not post the MVP financial ledger. A provider hold is retained as payment/provider evidence, not represented as settled money.

```text
Authorization approved for 10000 BRL
Ledger entries: none
```

Adding memorandum/hold accounts later requires an ADR because those entries must not be confused with captured funds.

## Full capture example

Payment `pay_001` is authorized for `10000` and captured in full.

| Account | Direction | Amount |
| --- | --- | ---: |
| `psp-clearing-receivable:BRL` | Debit | 10000 |
| `merchant-payable:merchant_001:BRL` | Credit | 10000 |

```text
Debits  = 10000
Credits = 10000
```

The transaction references the payment, capture command, PSP reference, idempotency record, and correlation identifier.

## Two partial captures

The same `10000` authorization is captured as `4000` and later `6000`. Each capture creates its own immutable transaction.

### Capture 1

| Account | Direction | Amount |
| --- | --- | ---: |
| `psp-clearing-receivable:BRL` | Debit | 4000 |
| `merchant-payable:merchant_001:BRL` | Credit | 4000 |

### Capture 2

| Account | Direction | Amount |
| --- | --- | ---: |
| `psp-clearing-receivable:BRL` | Debit | 6000 |
| `merchant-payable:merchant_001:BRL` | Credit | 6000 |

The payment reaches `CAPTURED`; the ledger contains four entries across two transactions.

## Partial refund example

Refund `3000` after the full `10000` capture:

| Account | Direction | Amount |
| --- | --- | ---: |
| `merchant-payable:merchant_001:BRL` | Debit | 3000 |
| `psp-clearing-receivable:BRL` | Credit | 3000 |

```text
Net merchant payable       = 10000 credit - 3000 debit = 7000 credit
Net PSP clearing receivable = 10000 debit - 3000 credit = 7000 debit
```

The payment becomes `PARTIALLY_REFUNDED`. The capture entries remain unchanged.

## Final refund example

Refund the remaining `7000`:

| Account | Direction | Amount |
| --- | --- | ---: |
| `merchant-payable:merchant_001:BRL` | Debit | 7000 |
| `psp-clearing-receivable:BRL` | Credit | 7000 |

The payment becomes `REFUNDED`, and both projected account balances return to zero for this payment.

## Compensating correction example

If reconciliation proves that an internal capture posting must be reversed, the original transaction is not edited. A new transaction references it through `reversesLedgerTransactionId`:

| Account | Direction | Amount |
| --- | --- | ---: |
| `merchant-payable:merchant_001:BRL` | Debit | 10000 |
| `psp-clearing-receivable:BRL` | Credit | 10000 |

The correction also references the reconciliation case, authenticated operator, reason, and audit event. A correction cannot silently hide the original evidence.

## Projection formulas

```text
Asset balance     = sum(debits) - sum(credits)
Liability balance = sum(credits) - sum(debits)
```

A rebuild deletes only projection rows, replays authoritative entries in deterministic order `(postedAt, ledgerTransactionId, entrySequence)`, and must reproduce the same balances and checkpoints.

## Persistence expectations

- unique business-effect reference prevents duplicate posting;
- entry sequence is unique within a transaction;
- transaction currency and entry currency agree;
- posting occurs in the same local PostgreSQL transaction as payment state and audit evidence;
- application APIs expose no update/delete operation for posted records;
- database roles used by the application are restricted to required statements;
- migration and repository tests prove append-only behavior.

Cross-row debit/credit equality is validated while the complete transaction is in memory and verified again in PostgreSQL integration tests before commit. If a later schema strategy adds deferred database enforcement, it requires evidence and an ADR.

## Required tests

- property-oriented balanced/unbalanced transaction generation;
- full and partial capture postings;
- partial and final refund postings;
- correction linked to the original transaction;
- duplicate business-effect rejection;
- attempted mutation/deletion rejection;
- atomic rollback when any entry fails;
- projection rebuild from an empty state;
- concurrency test proving payment totals and ledger totals agree after races.
