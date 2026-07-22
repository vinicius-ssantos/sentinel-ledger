package io.github.vinicius.sentinel.payments;

import io.github.vinicius.sentinel.merchant.MerchantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentIntentStore {
	void save(PaymentIntent paymentIntent);
	Optional<PaymentIntent> findOwned(PaymentIntentId id, MerchantId merchantId);

	/**
	 * Reads any payment intent regardless of merchant ownership. Reserved for the privileged operator API
	 * (reconciliation): unlike {@link #findOwned}, this performs no ownership check, so callers must be operating
	 * under operator authorization, never merchant authorization.
	 */
	Optional<PaymentIntent> findById(PaymentIntentId id);

	/**
	 * Payment intents still {@code AUTHORIZATION_PENDING} or {@code AUTHORIZATION_UNKNOWN} whose last update is
	 * older than {@code threshold} — candidates for a background reconciliation sweep to recover through provider
	 * status lookup, independent of whether the original client ever retries.
	 */
	List<PaymentIntentId> findAuthorizationPendingOlderThan(Instant threshold);
}
