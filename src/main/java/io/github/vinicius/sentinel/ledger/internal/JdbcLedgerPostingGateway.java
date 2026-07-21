package io.github.vinicius.sentinel.ledger.internal;

import io.github.vinicius.sentinel.ledger.AccountId;
import io.github.vinicius.sentinel.ledger.EntryDirection;
import io.github.vinicius.sentinel.ledger.LedgerEntry;
import io.github.vinicius.sentinel.ledger.LedgerPostingPort;
import io.github.vinicius.sentinel.ledger.LedgerTransaction;
import io.github.vinicius.sentinel.ledger.LedgerTransactionId;
import io.github.vinicius.sentinel.ledger.PostingOutcome;
import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcLedgerPostingGateway implements LedgerPostingPort {

	private final JdbcClient jdbcClient;

	JdbcLedgerPostingGateway(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	@Transactional
	public PostingOutcome post(LedgerTransaction transaction) {
		boolean inserted = insertTransactionHeader(transaction);
		if (!inserted) {
			LedgerTransaction existing = findByBusinessEffectReference(transaction.businessEffectReference())
				.orElseThrow(() -> new IllegalStateException(
					"business effect reference conflicted but no row was found: " + transaction.businessEffectReference()
				));
			return new PostingOutcome.AlreadyPosted(existing);
		}

		insertEntries(transaction);
		updateProjection(transaction);
		return new PostingOutcome.Posted(transaction);
	}

	Optional<LedgerTransaction> findByBusinessEffectReference(String businessEffectReference) {
		return jdbcClient.sql("""
				SELECT id, business_effect_reference, currency_code, currency_fraction_digits,
					reverses_ledger_transaction_id, posted_at
				FROM ledger_transactions
				WHERE business_effect_reference = :ref
				""")
			.param("ref", businessEffectReference)
			.query(JdbcLedgerPostingGateway::toHeader)
			.optional()
			.map(this::hydrate);
	}

	private boolean insertTransactionHeader(LedgerTransaction transaction) {
		int inserted = jdbcClient.sql("""
				INSERT INTO ledger_transactions (
					id, business_effect_reference, currency_code, currency_fraction_digits,
					reverses_ledger_transaction_id, posted_at
				) VALUES (
					:id, :businessEffectReference, :currencyCode, :currencyFractionDigits, :reversesId, :postedAt
				)
				ON CONFLICT (business_effect_reference) DO NOTHING
				""")
			.param("id", transaction.id().value())
			.param("businessEffectReference", transaction.businessEffectReference())
			.param("currencyCode", transaction.currency().code())
			.param("currencyFractionDigits", transaction.currency().fractionDigits())
			.param("reversesId", reversesIdOf(transaction))
			.param("postedAt", Timestamp.from(transaction.postedAt()))
			.update();
		return inserted == 1;
	}

	private void insertEntries(LedgerTransaction transaction) {
		List<LedgerEntry> entries = transaction.entries();
		for (int i = 0; i < entries.size(); i++) {
			LedgerEntry entry = entries.get(i);
			jdbcClient.sql("""
					INSERT INTO ledger_entries (id, ledger_transaction_id, entry_sequence, account_id, direction, amount_minor)
					VALUES (:id, :transactionId, :sequence, :accountId, :direction, :amountMinor)
					""")
				.param("id", UUID.randomUUID())
				.param("transactionId", transaction.id().value())
				.param("sequence", i + 1)
				.param("accountId", entry.accountId().value())
				.param("direction", entry.direction().name())
				.param("amountMinor", entry.amount().amountInMinorUnits())
				.update();
		}
	}

	private void updateProjection(LedgerTransaction transaction) {
		Instant now = Instant.now();
		for (LedgerEntry entry : transaction.entries()) {
			long debitDelta = entry.direction() == EntryDirection.DEBIT ? entry.amount().amountInMinorUnits() : 0;
			long creditDelta = entry.direction() == EntryDirection.CREDIT ? entry.amount().amountInMinorUnits() : 0;
			jdbcClient.sql("""
					INSERT INTO ledger_account_balance_projections (
						account_id, currency_code, currency_fraction_digits, debit_total_minor, credit_total_minor, updated_at
					) VALUES (
						:accountId, :currencyCode, :currencyFractionDigits, :debitDelta, :creditDelta, :now
					)
					ON CONFLICT (account_id) DO UPDATE SET
						debit_total_minor = ledger_account_balance_projections.debit_total_minor + EXCLUDED.debit_total_minor,
						credit_total_minor = ledger_account_balance_projections.credit_total_minor + EXCLUDED.credit_total_minor,
						updated_at = EXCLUDED.updated_at
					""")
				.param("accountId", entry.accountId().value())
				.param("currencyCode", transaction.currency().code())
				.param("currencyFractionDigits", transaction.currency().fractionDigits())
				.param("debitDelta", debitDelta)
				.param("creditDelta", creditDelta)
				.param("now", Timestamp.from(now))
				.update();
		}
	}

	private static UUID reversesIdOf(LedgerTransaction transaction) {
		return transaction.reversesLedgerTransactionId() == null ? null : transaction.reversesLedgerTransactionId().value();
	}

	private LedgerTransaction hydrate(TransactionHeader header) {
		Currency currency = new Currency(header.currencyCode(), header.currencyFractionDigits());
		List<LedgerEntry> entries = jdbcClient.sql("""
				SELECT account_id, direction, amount_minor
				FROM ledger_entries
				WHERE ledger_transaction_id = :id
				ORDER BY entry_sequence
				""")
			.param("id", header.id())
			.query((rs, rowNum) -> new LedgerEntry(
				new AccountId(rs.getString("account_id")),
				EntryDirection.valueOf(rs.getString("direction")),
				Money.ofMinor(rs.getLong("amount_minor"), currency)
			))
			.list();

		LedgerTransactionId id = new LedgerTransactionId(header.id());
		return header.reversesId() == null
			? LedgerTransaction.post(id, header.businessEffectReference(), currency, entries, header.postedAt())
			: LedgerTransaction.compensate(id, header.businessEffectReference(), currency, entries, new LedgerTransactionId(header.reversesId()), header.postedAt());
	}

	private record TransactionHeader(
		UUID id, String businessEffectReference, String currencyCode, int currencyFractionDigits, UUID reversesId, Instant postedAt
	) {}

	private static TransactionHeader toHeader(ResultSet rs, int rowNum) throws SQLException {
		return new TransactionHeader(
			rs.getObject("id", UUID.class),
			rs.getString("business_effect_reference"),
			rs.getString("currency_code"),
			rs.getInt("currency_fraction_digits"),
			(UUID) rs.getObject("reverses_ledger_transaction_id"),
			rs.getTimestamp("posted_at").toInstant()
		);
	}
}
