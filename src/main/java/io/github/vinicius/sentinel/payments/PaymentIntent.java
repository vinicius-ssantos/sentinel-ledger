package io.github.vinicius.sentinel.payments;

import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.money.Money;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PaymentIntent {

	private final PaymentIntentId id;
	private final MerchantId merchantId;
	private final Money amount;
	private PaymentIntentState state;
	private Money capturedAmount;
	private Money refundedAmount;
	private long version;
	private final Instant createdAt;
	private Instant updatedAt;
	private final List<PaymentIntentEvent> pendingEvents;

	private PaymentIntent(
		PaymentIntentId id,
		MerchantId merchantId,
		Money amount,
		PaymentIntentState state,
		Money capturedAmount,
		Money refundedAmount,
		long version,
		Instant createdAt,
		Instant updatedAt,
		List<PaymentIntentEvent> pendingEvents
	) {
		this.id = Objects.requireNonNull(id, "id must not be null");
		this.merchantId = Objects.requireNonNull(merchantId, "merchantId must not be null");
		this.amount = Objects.requireNonNull(amount, "amount must not be null");
		this.state = Objects.requireNonNull(state, "state must not be null");
		this.capturedAmount = Objects.requireNonNull(capturedAmount, "capturedAmount must not be null");
		this.refundedAmount = Objects.requireNonNull(refundedAmount, "refundedAmount must not be null");
		this.version = version;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
		this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
		this.pendingEvents = new ArrayList<>(pendingEvents);
		validateSnapshot();
	}

	public static PaymentIntent create(
		PaymentIntentId id,
		MerchantId merchantId,
		Money amount,
		Instant createdAt
	) {
		Objects.requireNonNull(amount, "amount must not be null");
		if (!amount.isPositive()) {
			throw invalidSnapshot("payment intent amount must be positive");
		}

		PaymentIntent intent = new PaymentIntent(
			id,
			merchantId,
			amount,
			PaymentIntentState.CREATED,
			Money.zero(amount.currency()),
			Money.zero(amount.currency()),
			0,
			createdAt,
			createdAt,
			List.of()
		);
		intent.pendingEvents.add(intent.event(
			PaymentIntentEventType.CREATED,
			null,
			PaymentIntentState.CREATED,
			amount,
			createdAt
		));
		return intent;
	}

	public static PaymentIntent restore(
		PaymentIntentId id,
		MerchantId merchantId,
		Money amount,
		PaymentIntentState state,
		Money capturedAmount,
		Money refundedAmount,
		long version,
		Instant createdAt,
		Instant updatedAt
	) {
		return new PaymentIntent(
			id,
			merchantId,
			amount,
			state,
			capturedAmount,
			refundedAmount,
			version,
			createdAt,
			updatedAt,
			List.of()
		);
	}

	public PaymentIntentDecision startAuthorization(Instant occurredAt) {
		return transition(
			PaymentIntentEventType.AUTHORIZATION_STARTED,
			PaymentIntentState.AUTHORIZATION_PENDING,
			Money.zero(amount.currency()),
			occurredAt
		);
	}

	public PaymentIntentDecision markAuthorizationUnknown(Instant occurredAt) {
		return transition(
			PaymentIntentEventType.AUTHORIZATION_BECAME_UNKNOWN,
			PaymentIntentState.AUTHORIZATION_UNKNOWN,
			Money.zero(amount.currency()),
			occurredAt
		);
	}

	public PaymentIntentDecision authorize(Instant occurredAt) {
		return transition(
			PaymentIntentEventType.AUTHORIZED,
			PaymentIntentState.AUTHORIZED,
			amount,
			occurredAt
		);
	}

	public PaymentIntentDecision decline(Instant occurredAt) {
		return transition(
			PaymentIntentEventType.DECLINED,
			PaymentIntentState.DECLINED,
			amount,
			occurredAt
		);
	}

	public PaymentIntentDecision failAuthorization(Instant occurredAt) {
		return transition(
			PaymentIntentEventType.FAILED,
			PaymentIntentState.FAILED,
			amount,
			occurredAt
		);
	}

	public PaymentIntentDecision cancel(Instant occurredAt) {
		return transition(
			PaymentIntentEventType.CANCELLED,
			PaymentIntentState.CANCELLED,
			Money.zero(amount.currency()),
			occurredAt
		);
	}

	public PaymentIntentDecision capture(Money captureAmount, Instant occurredAt) {
		PaymentIntentDecision validation = validateAmountCommand(
			captureAmount,
			occurredAt,
			PaymentIntentState.AUTHORIZED,
			PaymentIntentState.PARTIALLY_CAPTURED
		);
		if (validation != null) {
			return validation;
		}
		if (captureAmount.compareTo(remainingAuthorizedAmount()) > 0) {
			return PaymentIntentDecision.denied(
				PaymentIntentErrorCode.CAPTURE_EXCEEDS_AUTHORIZED_AMOUNT,
				"capture amount exceeds the remaining authorized amount"
			);
		}

		Money nextCapturedAmount = capturedAmount.add(captureAmount);
		PaymentIntentState target = nextCapturedAmount.equals(amount)
			? PaymentIntentState.CAPTURED
			: PaymentIntentState.PARTIALLY_CAPTURED;
		if (!state.canTransitionTo(target)) {
			return invalidTransition(target);
		}

		capturedAmount = nextCapturedAmount;
		return apply(
			PaymentIntentEventType.CAPTURED,
			target,
			captureAmount,
			occurredAt
		);
	}

	public PaymentIntentDecision refund(Money refundAmount, Instant occurredAt) {
		PaymentIntentDecision validation = validateAmountCommand(
			refundAmount,
			occurredAt,
			PaymentIntentState.PARTIALLY_CAPTURED,
			PaymentIntentState.CAPTURED,
			PaymentIntentState.PARTIALLY_REFUNDED
		);
		if (validation != null) {
			return validation;
		}
		if (refundAmount.compareTo(refundableAmount()) > 0) {
			return PaymentIntentDecision.denied(
				PaymentIntentErrorCode.REFUND_EXCEEDS_CAPTURED_AMOUNT,
				"refund amount exceeds the remaining captured amount"
			);
		}

		Money nextRefundedAmount = refundedAmount.add(refundAmount);
		PaymentIntentState target = nextRefundedAmount.equals(capturedAmount)
			? PaymentIntentState.REFUNDED
			: PaymentIntentState.PARTIALLY_REFUNDED;
		if (!state.canTransitionTo(target)) {
			return invalidTransition(target);
		}

		refundedAmount = nextRefundedAmount;
		return apply(
			PaymentIntentEventType.REFUNDED,
			target,
			refundAmount,
			occurredAt
		);
	}

	public Money remainingAuthorizedAmount() {
		return amount.subtract(capturedAmount);
	}

	public Money refundableAmount() {
		return capturedAmount.subtract(refundedAmount);
	}

	public List<PaymentIntentEvent> pendingEvents() {
		return List.copyOf(pendingEvents);
	}

	public List<PaymentIntentEvent> drainEvents() {
		List<PaymentIntentEvent> drained = List.copyOf(pendingEvents);
		pendingEvents.clear();
		return drained;
	}

	private PaymentIntentDecision transition(
		PaymentIntentEventType eventType,
		PaymentIntentState target,
		Money eventAmount,
		Instant occurredAt
	) {
		Objects.requireNonNull(occurredAt, "occurredAt must not be null");
		if (!state.canTransitionTo(target)) {
			return invalidTransition(target);
		}
		if (occurredAt.isBefore(updatedAt)) {
			return PaymentIntentDecision.denied(
				PaymentIntentErrorCode.NON_MONOTONIC_TIMESTAMP,
				"event timestamp must not be before the aggregate update timestamp"
			);
		}
		if (version == Long.MAX_VALUE) {
			return PaymentIntentDecision.denied(
				PaymentIntentErrorCode.VERSION_EXHAUSTED,
				"aggregate version cannot advance beyond Long.MAX_VALUE"
			);
		}
		return apply(eventType, target, eventAmount, occurredAt);
	}

	private PaymentIntentDecision validateAmountCommand(
		Money commandAmount,
		Instant occurredAt,
		PaymentIntentState... allowedStates
	) {
		Objects.requireNonNull(commandAmount, "command amount must not be null");
		Objects.requireNonNull(occurredAt, "occurredAt must not be null");
		if (!commandAmount.isPositive()) {
			return PaymentIntentDecision.denied(
				PaymentIntentErrorCode.INVALID_AMOUNT,
				"command amount must be positive"
			);
		}
		if (!amount.currency().equals(commandAmount.currency())) {
			return PaymentIntentDecision.denied(
				PaymentIntentErrorCode.CURRENCY_MISMATCH,
				"command currency must match the payment intent currency"
			);
		}
		boolean allowed = false;
		for (PaymentIntentState allowedState : allowedStates) {
			allowed |= state == allowedState;
		}
		if (!allowed) {
			return PaymentIntentDecision.denied(
				PaymentIntentErrorCode.INVALID_TRANSITION,
				"operation is not allowed from state " + state
			);
		}
		if (occurredAt.isBefore(updatedAt)) {
			return PaymentIntentDecision.denied(
				PaymentIntentErrorCode.NON_MONOTONIC_TIMESTAMP,
				"event timestamp must not be before the aggregate update timestamp"
			);
		}
		if (version == Long.MAX_VALUE) {
			return PaymentIntentDecision.denied(
				PaymentIntentErrorCode.VERSION_EXHAUSTED,
				"aggregate version cannot advance beyond Long.MAX_VALUE"
			);
		}
		return null;
	}

	private PaymentIntentDecision invalidTransition(PaymentIntentState target) {
		return PaymentIntentDecision.denied(
			PaymentIntentErrorCode.INVALID_TRANSITION,
			"transition from " + state + " to " + target + " is not allowed"
		);
	}

	private PaymentIntentDecision apply(
		PaymentIntentEventType eventType,
		PaymentIntentState target,
		Money eventAmount,
		Instant occurredAt
	) {
		PaymentIntentState previousState = state;
		state = target;
		version = Math.incrementExact(version);
		updatedAt = occurredAt;
		PaymentIntentEvent event = event(eventType, previousState, target, eventAmount, occurredAt);
		pendingEvents.add(event);
		return PaymentIntentDecision.applied(event);
	}

	private PaymentIntentEvent event(
		PaymentIntentEventType eventType,
		PaymentIntentState previousState,
		PaymentIntentState currentState,
		Money eventAmount,
		Instant occurredAt
	) {
		return new PaymentIntentEvent(
			eventType,
			id,
			previousState,
			currentState,
			eventAmount,
			version,
			occurredAt
		);
	}

	private void validateSnapshot() {
		if (!amount.isPositive()) {
			throw invalidSnapshot("payment intent amount must be positive");
		}
		if (version < 0) {
			throw invalidSnapshot("version must not be negative");
		}
		if (updatedAt.isBefore(createdAt)) {
			throw invalidSnapshot("updatedAt must not be before createdAt");
		}
		if (!amount.currency().equals(capturedAmount.currency())
			|| !amount.currency().equals(refundedAmount.currency())) {
			throw invalidSnapshot("all monetary values must use the payment intent currency");
		}
		if (capturedAmount.isNegative() || refundedAmount.isNegative()) {
			throw invalidSnapshot("captured and refunded amounts must not be negative");
		}
		if (capturedAmount.compareTo(amount) > 0) {
			throw invalidSnapshot("captured amount must not exceed the payment intent amount");
		}
		if (refundedAmount.compareTo(capturedAmount) > 0) {
			throw invalidSnapshot("refunded amount must not exceed the captured amount");
		}
		validateStateTotals();
	}

	private void validateStateTotals() {
		boolean noFinancialEffect = capturedAmount.isZero() && refundedAmount.isZero();
		switch (state) {
			case CREATED, AUTHORIZATION_PENDING, AUTHORIZATION_UNKNOWN, AUTHORIZED,
				DECLINED, FAILED, CANCELLED -> {
				if (!noFinancialEffect) {
					throw invalidSnapshot("state " + state + " cannot contain captured or refunded value");
				}
			}
			case PARTIALLY_CAPTURED -> {
				if (!capturedAmount.isPositive()
					|| capturedAmount.equals(amount)
					|| !refundedAmount.isZero()) {
					throw invalidSnapshot("PARTIALLY_CAPTURED requires a partial capture and no refund");
				}
			}
			case CAPTURED -> {
				if (!capturedAmount.equals(amount) || !refundedAmount.isZero()) {
					throw invalidSnapshot("CAPTURED requires the full amount captured and no refund");
				}
			}
			case PARTIALLY_REFUNDED -> {
				if (!capturedAmount.isPositive()
					|| !refundedAmount.isPositive()
					|| refundedAmount.equals(capturedAmount)) {
					throw invalidSnapshot("PARTIALLY_REFUNDED requires a refund below captured value");
				}
			}
			case REFUNDED -> {
				if (!capturedAmount.isPositive() || !refundedAmount.equals(capturedAmount)) {
					throw invalidSnapshot("REFUNDED requires all captured value to be refunded");
				}
			}
		}
	}

	private static IllegalArgumentException invalidSnapshot(String detail) {
		return new IllegalArgumentException(
			PaymentIntentErrorCode.INVALID_AGGREGATE_STATE + ": " + detail
		);
	}

	public PaymentIntentId id() {
		return id;
	}

	public MerchantId merchantId() {
		return merchantId;
	}

	public Money amount() {
		return amount;
	}

	public PaymentIntentState state() {
		return state;
	}

	public Money capturedAmount() {
		return capturedAmount;
	}

	public Money refundedAmount() {
		return refundedAmount;
	}

	public long version() {
		return version;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}
}
