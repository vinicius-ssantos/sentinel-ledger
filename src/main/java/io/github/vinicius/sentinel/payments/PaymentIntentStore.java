package io.github.vinicius.sentinel.payments;

import io.github.vinicius.sentinel.merchant.MerchantId;

import java.util.Optional;

public interface PaymentIntentStore {
	void save(PaymentIntent paymentIntent);
	Optional<PaymentIntent> findOwned(PaymentIntentId id, MerchantId merchantId);
}
