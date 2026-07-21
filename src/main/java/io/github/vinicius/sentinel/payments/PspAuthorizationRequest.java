package io.github.vinicius.sentinel.payments;

import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.money.Money;

import java.util.Objects;

public record PspAuthorizationRequest(
	PspAttemptId attemptId,
	PaymentIntentId paymentIntentId,
	MerchantId merchantId,
	Money amount
) {

	public PspAuthorizationRequest {
		Objects.requireNonNull(attemptId, "attemptId must not be null");
		Objects.requireNonNull(paymentIntentId, "paymentIntentId must not be null");
		Objects.requireNonNull(merchantId, "merchantId must not be null");
		Objects.requireNonNull(amount, "amount must not be null");
		if (!amount.isPositive()) {
			throw new IllegalArgumentException("amount must be positive");
		}
	}
}
