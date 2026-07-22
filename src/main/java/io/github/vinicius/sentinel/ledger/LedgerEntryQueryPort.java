package io.github.vinicius.sentinel.ledger;

public interface LedgerEntryQueryPort {

	/**
	 * Entries for {@code accountId}, ordered {@code postedAt} then transaction/sequence ascending, starting after
	 * {@code cursor} (an opaque token from a previous {@link LedgerEntryPage#nextCursor()}, or {@code null} for
	 * the first page). {@code limit} is the maximum number of entries to return.
	 */
	LedgerEntryPage findByAccount(AccountId accountId, String cursor, int limit);
}
