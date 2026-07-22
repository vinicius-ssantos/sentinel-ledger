package io.github.vinicius.sentinel.ledger.internal;

import io.github.vinicius.sentinel.ledger.AccountId;
import io.github.vinicius.sentinel.ledger.EntryDirection;
import io.github.vinicius.sentinel.ledger.LedgerEntryPage;
import io.github.vinicius.sentinel.ledger.LedgerEntryQueryPort;
import io.github.vinicius.sentinel.ledger.LedgerTransactionId;
import io.github.vinicius.sentinel.ledger.PostedLedgerEntry;
import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Keyset (not offset) pagination on {@code (posted_at, ledger_transaction_id, entry_sequence)}: appends after a
 * page is read can never shift already-returned rows out from under the next request, so a caller can never skip
 * or duplicate an entry while paging through append activity.
 */
@Repository
class JdbcLedgerEntryQueryGateway implements LedgerEntryQueryPort {

	private final JdbcClient jdbcClient;

	JdbcLedgerEntryQueryGateway(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public LedgerEntryPage findByAccount(AccountId accountId, String cursor, int limit) {
		Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
		String sql = decoded == null
			? """
				SELECT e.ledger_transaction_id, e.entry_sequence, t.business_effect_reference, e.account_id,
					e.direction, e.amount_minor, e.posted_at, t.currency_code, t.currency_fraction_digits
				FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.ledger_transaction_id
				WHERE e.account_id = :accountId
				ORDER BY e.posted_at ASC, e.ledger_transaction_id ASC, e.entry_sequence ASC
				LIMIT :fetchLimit
				"""
			: """
				SELECT e.ledger_transaction_id, e.entry_sequence, t.business_effect_reference, e.account_id,
					e.direction, e.amount_minor, e.posted_at, t.currency_code, t.currency_fraction_digits
				FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.ledger_transaction_id
				WHERE e.account_id = :accountId
					AND (e.posted_at, e.ledger_transaction_id, e.entry_sequence) > (:cursorPostedAt, :cursorTxId, :cursorSeq)
				ORDER BY e.posted_at ASC, e.ledger_transaction_id ASC, e.entry_sequence ASC
				LIMIT :fetchLimit
				""";

		var spec = jdbcClient.sql(sql)
			.param("accountId", accountId.value())
			.param("fetchLimit", limit + 1);
		if (decoded != null) {
			spec = spec
				.param("cursorPostedAt", Timestamp.from(decoded.postedAt()))
				.param("cursorTxId", decoded.ledgerTransactionId())
				.param("cursorSeq", decoded.entrySequence());
		}

		List<PostedLedgerEntry> fetched = spec.query(JdbcLedgerEntryQueryGateway::toEntry).list();

		boolean hasMore = fetched.size() > limit;
		List<PostedLedgerEntry> page = hasMore ? fetched.subList(0, limit) : fetched;
		String nextCursor = hasMore ? Cursor.of(page.getLast()).encode() : null;
		return new LedgerEntryPage(page, nextCursor);
	}

	private static PostedLedgerEntry toEntry(ResultSet rs, int rowNum) throws SQLException {
		Currency currency = new Currency(rs.getString("currency_code"), rs.getInt("currency_fraction_digits"));
		return new PostedLedgerEntry(
			new LedgerTransactionId(rs.getObject("ledger_transaction_id", UUID.class)),
			rs.getInt("entry_sequence"),
			rs.getString("business_effect_reference"),
			new AccountId(rs.getString("account_id")),
			EntryDirection.valueOf(rs.getString("direction")),
			Money.ofMinor(rs.getLong("amount_minor"), currency),
			rs.getTimestamp("posted_at").toInstant()
		);
	}

	private record Cursor(Instant postedAt, UUID ledgerTransactionId, int entrySequence) {

		static Cursor of(PostedLedgerEntry entry) {
			return new Cursor(entry.postedAt(), entry.ledgerTransactionId().value(), entry.entrySequence());
		}

		String encode() {
			String payload = postedAt + "|" + ledgerTransactionId + "|" + entrySequence;
			return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
		}

		static Cursor decode(String token) {
			try {
				String payload = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
				String[] parts = payload.split("\\|", 3);
				return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]), Integer.parseInt(parts[2]));
			} catch (RuntimeException e) {
				throw new IllegalArgumentException("invalid ledger entry cursor", e);
			}
		}
	}
}
