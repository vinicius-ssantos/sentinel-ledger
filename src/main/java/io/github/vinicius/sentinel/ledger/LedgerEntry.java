package io.github.vinicius.sentinel.ledger;

import io.github.vinicius.sentinel.money.Money;

import java.util.Objects;

public record LedgerEntry(AccountId accountId, EntryDirection direction, Money amount) {

	public LedgerEntry {
		Objects.requireNonNull(accountId, "accountId must not be null");
		Objects.requireNonNull(direction, "direction must not be null");
		Objects.requireNonNull(amount, "amount must not be null");
		if (!amount.isPositive()) {
			throw new IllegalArgumentException("entry amount must be positive; direction represents debit/credit");
		}
	}
}
