package io.github.vinicius.sentinel.payments;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum PaymentIntentState {
	CREATED,
	AUTHORIZATION_PENDING,
	AUTHORIZATION_UNKNOWN,
	AUTHORIZED,
	PARTIALLY_CAPTURED,
	CAPTURED,
	PARTIALLY_REFUNDED,
	REFUNDED,
	DECLINED,
	FAILED,
	CANCELLED;

	private static final Map<PaymentIntentState, Set<PaymentIntentState>> ALLOWED_TRANSITIONS = Map.ofEntries(
		Map.entry(CREATED, EnumSet.of(AUTHORIZATION_PENDING, CANCELLED)),
		Map.entry(AUTHORIZATION_PENDING, EnumSet.of(AUTHORIZATION_UNKNOWN, AUTHORIZED, DECLINED, FAILED)),
		Map.entry(AUTHORIZATION_UNKNOWN, EnumSet.of(AUTHORIZED, DECLINED, FAILED)),
		Map.entry(AUTHORIZED, EnumSet.of(PARTIALLY_CAPTURED, CAPTURED, CANCELLED)),
		Map.entry(PARTIALLY_CAPTURED, EnumSet.of(PARTIALLY_CAPTURED, CAPTURED, PARTIALLY_REFUNDED, REFUNDED)),
		Map.entry(CAPTURED, EnumSet.of(PARTIALLY_REFUNDED, REFUNDED)),
		Map.entry(PARTIALLY_REFUNDED, EnumSet.of(PARTIALLY_REFUNDED, REFUNDED)),
		Map.entry(REFUNDED, EnumSet.noneOf(PaymentIntentState.class)),
		Map.entry(DECLINED, EnumSet.noneOf(PaymentIntentState.class)),
		Map.entry(FAILED, EnumSet.noneOf(PaymentIntentState.class)),
		Map.entry(CANCELLED, EnumSet.noneOf(PaymentIntentState.class))
	);

	public boolean canTransitionTo(PaymentIntentState target) {
		return ALLOWED_TRANSITIONS.get(this).contains(target);
	}

	public boolean isTerminal() {
		return ALLOWED_TRANSITIONS.get(this).isEmpty();
	}
}
