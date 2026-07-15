package io.github.vinicius.sentinel.payments;

import java.util.Objects;
import java.util.UUID;

public record PaymentIntentId(UUID value) {
	public PaymentIntentId {
		Objects.requireNonNull(value, "payment intent id must not be null");
	}

	public static PaymentIntentId generate() {
		return new PaymentIntentId(UUID.randomUUID());
	}
}
