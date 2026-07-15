package io.github.vinicius.sentinel.payments;

public enum PaymentIntentEventType {
	CREATED,
	AUTHORIZATION_STARTED,
	AUTHORIZATION_BECAME_UNKNOWN,
	AUTHORIZED,
	DECLINED,
	FAILED,
	CAPTURED,
	REFUNDED,
	CANCELLED
}
