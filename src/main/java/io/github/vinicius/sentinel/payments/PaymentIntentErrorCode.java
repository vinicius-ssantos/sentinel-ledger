package io.github.vinicius.sentinel.payments;

public enum PaymentIntentErrorCode {
	INVALID_TRANSITION,
	CAPTURE_EXCEEDS_AUTHORIZED_AMOUNT,
	REFUND_EXCEEDS_CAPTURED_AMOUNT,
	INVALID_AGGREGATE_STATE
}
