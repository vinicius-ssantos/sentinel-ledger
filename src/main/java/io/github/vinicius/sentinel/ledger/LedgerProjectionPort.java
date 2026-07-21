package io.github.vinicius.sentinel.ledger;

import io.github.vinicius.sentinel.money.Money;

import java.util.Optional;

public interface LedgerProjectionPort {

	/** The account's current projected balance, or empty if no entry has ever posted against it. */
	Optional<Money> currentBalance(AccountId accountId);

	/**
	 * Deletes every projection row and recomputes each account's balance from the authoritative
	 * {@code ledger_entries}. Never touches transactions or entries themselves.
	 */
	void rebuild();
}
