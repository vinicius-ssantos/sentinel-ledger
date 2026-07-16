package io.github.vinicius.sentinel.payments.internal;

import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.payments.PaymentIntent;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PaymentIntentStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
final class JdbcPaymentIntentStore implements PaymentIntentStore {
	private final JdbcClient jdbcClient;

	JdbcPaymentIntentStore(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public void save(PaymentIntent paymentIntent) {
		throw new UnsupportedOperationException("not implemented");
	}

	@Override
	public Optional<PaymentIntent> findOwned(PaymentIntentId id, MerchantId merchantId) {
		return Optional.empty();
	}
}
