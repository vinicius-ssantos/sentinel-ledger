package io.github.vinicius.sentinel.payments.internal.web;

import io.github.vinicius.sentinel.payments.PaymentIntent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record PaymentIntentResponse(

	UUID id,

	@Schema(example = "12500")
	String amountInMinorUnits,

	@Schema(example = "BRL")
	String currency,

	@Schema(example = "CREATED")
	String state,

	@Schema(example = "0")
	String capturedAmountInMinorUnits,

	@Schema(example = "0")
	String refundedAmountInMinorUnits,

	Instant createdAt,
	Instant updatedAt
) {

	public static PaymentIntentResponse from(PaymentIntent paymentIntent) {
		return new PaymentIntentResponse(
			paymentIntent.id().value(),
			paymentIntent.amount().amountInMinorUnitsText(),
			paymentIntent.amount().currency().code(),
			paymentIntent.state().name(),
			paymentIntent.capturedAmount().amountInMinorUnitsText(),
			paymentIntent.refundedAmount().amountInMinorUnitsText(),
			paymentIntent.createdAt(),
			paymentIntent.updatedAt()
		);
	}
}
