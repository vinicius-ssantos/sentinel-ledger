package io.github.vinicius.sentinel.ledger;

import java.util.List;

public interface LedgerPostingPort {

	/**
	 * Posts a balanced transaction atomically. If a transaction with the same
	 * {@link LedgerTransaction#businessEffectReference()} was already posted, the existing transaction is
	 * returned instead of posting a duplicate.
	 */
	PostingOutcome post(LedgerTransaction transaction);

	/**
	 * Every transaction whose {@link LedgerTransaction#businessEffectReference()} starts with {@code prefix},
	 * ordered by {@code postedAt} ascending. Lets a consuming module (which defines its own business effect
	 * reference convention, such as {@code "capture:" + paymentIntentId + ":"}) correlate ledger evidence back to
	 * its own resource without the ledger module knowing that resource's vocabulary.
	 */
	List<LedgerTransaction> findByBusinessEffectReferencePrefix(String prefix);
}
