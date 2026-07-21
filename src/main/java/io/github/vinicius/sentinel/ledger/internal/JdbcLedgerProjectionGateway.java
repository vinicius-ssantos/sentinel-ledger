package io.github.vinicius.sentinel.ledger.internal;

import io.github.vinicius.sentinel.ledger.AccountId;
import io.github.vinicius.sentinel.ledger.AccountType;
import io.github.vinicius.sentinel.ledger.LedgerProjectionPort;
import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
class JdbcLedgerProjectionGateway implements LedgerProjectionPort {

	private final JdbcClient jdbcClient;

	JdbcLedgerProjectionGateway(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public Optional<Money> currentBalance(AccountId accountId) {
		return jdbcClient.sql("""
				SELECT currency_code, currency_fraction_digits, debit_total_minor, credit_total_minor
				FROM ledger_account_balance_projections
				WHERE account_id = :accountId
				""")
			.param("accountId", accountId.value())
			.query((rs, rowNum) -> toBalance(accountId, rs))
			.optional();
	}

	@Override
	@Transactional
	public void rebuild() {
		jdbcClient.sql("DELETE FROM ledger_account_balance_projections").update();
		jdbcClient.sql("""
				INSERT INTO ledger_account_balance_projections (
					account_id, currency_code, currency_fraction_digits, debit_total_minor, credit_total_minor, updated_at
				)
				SELECT
					e.account_id,
					t.currency_code,
					t.currency_fraction_digits,
					COALESCE(SUM(CASE WHEN e.direction = 'DEBIT' THEN e.amount_minor ELSE 0 END), 0),
					COALESCE(SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount_minor ELSE 0 END), 0),
					:now
				FROM ledger_entries e
				JOIN ledger_transactions t ON t.id = e.ledger_transaction_id
				GROUP BY e.account_id, t.currency_code, t.currency_fraction_digits
				""")
			.param("now", Timestamp.from(Instant.now()))
			.update();
	}

	private static Money toBalance(AccountId accountId, ResultSet rs) throws SQLException {
		Currency currency = new Currency(rs.getString("currency_code"), rs.getInt("currency_fraction_digits"));
		long debit = rs.getLong("debit_total_minor");
		long credit = rs.getLong("credit_total_minor");
		long balanceMinor = accountId.type() == AccountType.ASSET ? debit - credit : credit - debit;
		return Money.ofMinor(balanceMinor, currency);
	}
}
