package io.github.vinicius.sentinel.payments;

import java.util.Objects;

public record PaymentIntentDecision(
	Status status,
	PaymentIntentErrorCode errorCode,
	String detail,
	PaymentIntentEvent event
) {
	public enum Status {
		APPLIED,
		DENIED
	}

	public PaymentIntentDecision {
		Objects.requireNonNull(status, "status must not be null");
		if (status == Status.APPLIED) {
			Objects.requireNonNull(event, "applied decision must contain an event");
			if (errorCode != null || detail != null) {
				throw new IllegalArgumentException("applied decision must not contain an error");
			}
		} else {
			Objects.requireNonNull(errorCode, "denied decision must contain an error code");
			Objects.requireNonNull(detail, "denied decision must contain detail");
			if (event != null) {
				throw new IllegalArgumentException("denied decision must not contain an event");
			}
		}
	}

	public static PaymentIntentDecision applied(PaymentIntentEvent event) {
		return new PaymentIntentDecision(Status.APPLIED, null, null, event);
	}

	public static PaymentIntentDecision denied(PaymentIntentErrorCode errorCode, String detail) {
		return new PaymentIntentDecision(Status.DENIED, errorCode, detail, null);
	}

	public boolean wasApplied() {
		return status == Status.APPLIED;
	}
}
