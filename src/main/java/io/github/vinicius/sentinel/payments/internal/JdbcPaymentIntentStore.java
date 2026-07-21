package io.github.vinicius.sentinel.payments.internal;

import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
import io.github.vinicius.sentinel.payments.OptimisticLockException;
import io.github.vinicius.sentinel.payments.PaymentIntent;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PaymentIntentState;
import io.github.vinicius.sentinel.payments.PaymentIntentStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcPaymentIntentStore implements PaymentIntentStore {

	private final JdbcClient jdbcClient;

	JdbcPaymentIntentStore(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public void save(PaymentIntent paymentIntent) {
		int affected = jdbcClient.sql("""
				INSERT INTO payment_intents (
					id, merchant_id, amount_minor, currency_code, currency_fraction_digits,
					state, captured_amount_minor, refunded_amount_minor, aggregate_version,
					created_at, updated_at
				) VALUES (
					:id, :merchantId, :amountMinor, :currencyCode, :currencyFractionDigits,
					:state, :capturedAmountMinor, :refundedAmountMinor, :aggregateVersion,
					:createdAt, :updatedAt
				)
				ON CONFLICT (id) DO UPDATE SET
					state = EXCLUDED.state,
					captured_amount_minor = EXCLUDED.captured_amount_minor,
					refunded_amount_minor = EXCLUDED.refunded_amount_minor,
					aggregate_version = EXCLUDED.aggregate_version,
					updated_at = EXCLUDED.updated_at
				WHERE payment_intents.aggregate_version = EXCLUDED.aggregate_version - 1
				""")
			.param("id", paymentIntent.id().value())
			.param("merchantId", paymentIntent.merchantId().value())
			.param("amountMinor", paymentIntent.amount().amountInMinorUnits())
			.param("currencyCode", paymentIntent.amount().currency().code())
			.param("currencyFractionDigits", paymentIntent.amount().currency().fractionDigits())
			.param("state", paymentIntent.state().name())
			.param("capturedAmountMinor", paymentIntent.capturedAmount().amountInMinorUnits())
			.param("refundedAmountMinor", paymentIntent.refundedAmount().amountInMinorUnits())
			.param("aggregateVersion", paymentIntent.version())
			.param("createdAt", Timestamp.from(paymentIntent.createdAt()))
			.param("updatedAt", Timestamp.from(paymentIntent.updatedAt()))
			.update();

		if (affected == 0) {
			throw new OptimisticLockException(paymentIntent.id(), paymentIntent.version());
		}
	}

	@Override
	public Optional<PaymentIntent> findOwned(PaymentIntentId id, MerchantId merchantId) {
		return jdbcClient.sql("""
				SELECT id, merchant_id, amount_minor, currency_code, currency_fraction_digits,
					state, captured_amount_minor, refunded_amount_minor, aggregate_version,
					created_at, updated_at
				FROM payment_intents
				WHERE id = :id AND merchant_id = :merchantId
				""")
			.param("id", id.value())
			.param("merchantId", merchantId.value())
			.query(JdbcPaymentIntentStore::toPaymentIntent)
			.optional();
	}

	private static PaymentIntent toPaymentIntent(ResultSet rs, int rowNum) throws SQLException {
		Currency currency = new Currency(rs.getString("currency_code"), rs.getInt("currency_fraction_digits"));
		return PaymentIntent.restore(
			new PaymentIntentId(rs.getObject("id", UUID.class)),
			new MerchantId(rs.getObject("merchant_id", UUID.class)),
			Money.ofMinor(rs.getLong("amount_minor"), currency),
			PaymentIntentState.valueOf(rs.getString("state")),
			Money.ofMinor(rs.getLong("captured_amount_minor"), currency),
			Money.ofMinor(rs.getLong("refunded_amount_minor"), currency),
			rs.getLong("aggregate_version"),
			rs.getTimestamp("created_at").toInstant(),
			rs.getTimestamp("updated_at").toInstant()
		);
	}
}
