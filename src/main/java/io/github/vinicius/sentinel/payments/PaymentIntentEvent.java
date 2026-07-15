package io.github.vinicius.sentinel.payments;

import io.github.vinicius.sentinel.money.Money;
import java.time.Instant;

public record PaymentIntentEvent(
	PaymentIntentEventType type,
	PaymentIntentId paymentIntentId,
	PaymentIntentState previousState,
	PaymentIntentState currentState,
	Money amount,
	long version,
	Instant occurredAt
) {}
