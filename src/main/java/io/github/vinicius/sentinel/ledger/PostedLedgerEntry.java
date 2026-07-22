package io.github.vinicius.sentinel.ledger;

import io.github.vinicius.sentinel.money.Money;

import java.time.Instant;
import java.util.Objects;

/**
 * A read projection of one posted entry for account browsing, distinct from {@link LedgerEntry} (the write-side
 * value object): it carries the transaction context a caller needs to correlate the entry back to its business
 * effect without a second lookup.
 */
public record PostedLedgerEntry(
	LedgerTransactionId ledgerTransactionId,
	int entrySequence,
	String businessEffectReference,
	AccountId accountId,
	EntryDirection direction,
	Money amount,
	Instant postedAt
) {

	public PostedLedgerEntry {
		Objects.requireNonNull(ledgerTransactionId, "ledgerTransactionId must not be null");
		Objects.requireNonNull(businessEffectReference, "businessEffectReference must not be null");
		Objects.requireNonNull(accountId, "accountId must not be null");
		Objects.requireNonNull(direction, "direction must not be null");
		Objects.requireNonNull(amount, "amount must not be null");
		Objects.requireNonNull(postedAt, "postedAt must not be null");
	}
}
