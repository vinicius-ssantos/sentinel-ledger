package io.github.vinicius.sentinel.ledger;

public interface LedgerPostingPort {

	/**
	 * Posts a balanced transaction atomically. If a transaction with the same
	 * {@link LedgerTransaction#businessEffectReference()} was already posted, the existing transaction is
	 * returned instead of posting a duplicate.
	 */
	PostingOutcome post(LedgerTransaction transaction);
}
