package io.github.vinicius.sentinel.payments;

import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PaymentIntentTests {

	private static final PaymentIntentId PAYMENT_ID = new PaymentIntentId(
		UUID.fromString("3b62f3d7-d810-41c7-bf81-df064bb3a704")
	);
	private static final MerchantId MERCHANT_ID = new MerchantId(
		UUID.fromString("4fef9603-a322-4cef-a27f-48d2779271a1")
	);
	private static final Instant CREATED_AT = Instant.parse("2026-07-15T12:00:00Z");
	private static final Money TOTAL = Money.positive(10_000, Currency.BRL);

	@Test
	void createsAnOwnedIntentWithZeroFinancialTotalsAndOneDomainEvent() {
		PaymentIntent intent = newIntent();

		assertThat(intent.id()).isEqualTo(PAYMENT_ID);
		assertThat(intent.merchantId()).isEqualTo(MERCHANT_ID);
		assertThat(intent.amount()).isEqualTo(TOTAL);
		assertThat(intent.state()).isEqualTo(PaymentIntentState.CREATED);
		assertThat(intent.capturedAmount()).isEqualTo(Money.zero(Currency.BRL));
		assertThat(intent.refundedAmount()).isEqualTo(Money.zero(Currency.BRL));
		assertThat(intent.version()).isZero();
		assertThat(intent.createdAt()).isEqualTo(CREATED_AT);
		assertThat(intent.updatedAt()).isEqualTo(CREATED_AT);

		PaymentIntentEvent event = intent.pendingEvents().getFirst();
		assertThat(event.type()).isEqualTo(PaymentIntentEventType.CREATED);
		assertThat(event.previousState()).isNull();
		assertThat(event.currentState()).isEqualTo(PaymentIntentState.CREATED);
		assertThat(event.amount()).isEqualTo(TOTAL);
		assertThat(event.version()).isZero();
	}

	@Test
	void recoversAnUnknownAuthorizationOnlyThroughAProviderProvenOutcome() {
		PaymentIntent intent = newIntent();
		intent.drainEvents();

		PaymentIntentDecision started = intent.startAuthorization(CREATED_AT.plusSeconds(1));
		PaymentIntentDecision unknown = intent.markAuthorizationUnknown(CREATED_AT.plusSeconds(2));
		PaymentIntentDecision recovered = intent.authorize(CREATED_AT.plusSeconds(3));

		assertThat(started.wasApplied()).isTrue();
		assertThat(unknown.wasApplied()).isTrue();
		assertThat(recovered.wasApplied()).isTrue();
		assertThat(intent.state()).isEqualTo(PaymentIntentState.AUTHORIZED);
		assertThat(intent.version()).isEqualTo(3);
		assertThat(intent.pendingEvents())
			.extracting(PaymentIntentEvent::type)
			.containsExactly(
				PaymentIntentEventType.AUTHORIZATION_STARTED,
				PaymentIntentEventType.AUTHORIZATION_BECAME_UNKNOWN,
				PaymentIntentEventType.AUTHORIZED
			);
	}

	@Test
	void rejectsInvalidTransitionsWithoutChangingAggregateHistory() {
		PaymentIntent intent = newIntent();
		int eventCount = intent.pendingEvents().size();

		PaymentIntentDecision decision = intent.authorize(CREATED_AT.plusSeconds(1));

		assertThat(decision.wasApplied()).isFalse();
		assertThat(decision.errorCode()).isEqualTo(PaymentIntentErrorCode.INVALID_TRANSITION);
		assertThat(intent.state()).isEqualTo(PaymentIntentState.CREATED);
		assertThat(intent.version()).isZero();
		assertThat(intent.updatedAt()).isEqualTo(CREATED_AT);
		assertThat(intent.pendingEvents()).hasSize(eventCount);
	}

	@Test
	void appliesPartialCapturesUntilTheAuthorizedAmountIsFullyCaptured() {
		PaymentIntent intent = authorizedIntent();
		intent.drainEvents();

		PaymentIntentDecision first = intent.capture(
			Money.positive(4_000, Currency.BRL),
			CREATED_AT.plusSeconds(3)
		);
		PaymentIntentDecision second = intent.capture(
			Money.positive(6_000, Currency.BRL),
			CREATED_AT.plusSeconds(4)
		);

		assertThat(first.wasApplied()).isTrue();
		assertThat(first.event().currentState()).isEqualTo(PaymentIntentState.PARTIALLY_CAPTURED);
		assertThat(second.wasApplied()).isTrue();
		assertThat(second.event().currentState()).isEqualTo(PaymentIntentState.CAPTURED);
		assertThat(intent.capturedAmount()).isEqualTo(TOTAL);
		assertThat(intent.remainingAuthorizedAmount()).isEqualTo(Money.zero(Currency.BRL));
		assertThat(intent.state()).isEqualTo(PaymentIntentState.CAPTURED);
		assertThat(intent.version()).isEqualTo(4);
	}

	@Test
	void rejectsCaptureAboveTheRemainingAuthorizationWithoutPartialMutation() {
		PaymentIntent intent = authorizedIntent();
		intent.capture(Money.positive(8_000, Currency.BRL), CREATED_AT.plusSeconds(3));
		long versionBeforeRejection = intent.version();
		Instant updatedAtBeforeRejection = intent.updatedAt();

		PaymentIntentDecision decision = intent.capture(
			Money.positive(2_001, Currency.BRL),
			CREATED_AT.plusSeconds(4)
		);

		assertThat(decision.wasApplied()).isFalse();
		assertThat(decision.errorCode())
			.isEqualTo(PaymentIntentErrorCode.CAPTURE_EXCEEDS_AUTHORIZED_AMOUNT);
		assertThat(intent.capturedAmount()).isEqualTo(Money.ofMinor(8_000, Currency.BRL));
		assertThat(intent.version()).isEqualTo(versionBeforeRejection);
		assertThat(intent.updatedAt()).isEqualTo(updatedAtBeforeRejection);
	}

	@Test
	void rejectsNonPositiveAndCrossCurrencyFinancialCommands() {
		PaymentIntent intent = authorizedIntent();
		Currency usd = new Currency("USD", 2);

		PaymentIntentDecision zero = intent.capture(
			Money.zero(Currency.BRL),
			CREATED_AT.plusSeconds(3)
		);
		PaymentIntentDecision foreign = intent.capture(
			Money.positive(100, usd),
			CREATED_AT.plusSeconds(3)
		);

		assertThat(zero.errorCode()).isEqualTo(PaymentIntentErrorCode.INVALID_AMOUNT);
		assertThat(foreign.errorCode()).isEqualTo(PaymentIntentErrorCode.CURRENCY_MISMATCH);
		assertThat(intent.capturedAmount()).isEqualTo(Money.zero(Currency.BRL));
		assertThat(intent.version()).isEqualTo(2);
	}

	@Test
	void appliesPartialRefundsUntilAllCapturedValueIsRefunded() {
		PaymentIntent intent = authorizedIntent();
		intent.capture(TOTAL, CREATED_AT.plusSeconds(3));
		intent.drainEvents();

		PaymentIntentDecision first = intent.refund(
			Money.positive(3_000, Currency.BRL),
			CREATED_AT.plusSeconds(4)
		);
		PaymentIntentDecision second = intent.refund(
			Money.positive(7_000, Currency.BRL),
			CREATED_AT.plusSeconds(5)
		);

		assertThat(first.event().currentState()).isEqualTo(PaymentIntentState.PARTIALLY_REFUNDED);
		assertThat(second.event().currentState()).isEqualTo(PaymentIntentState.REFUNDED);
		assertThat(intent.refundedAmount()).isEqualTo(TOTAL);
		assertThat(intent.refundableAmount()).isEqualTo(Money.zero(Currency.BRL));
		assertThat(intent.state()).isEqualTo(PaymentIntentState.REFUNDED);
	}

	@Test
	void rejectsRefundAboveCapturedValueAndForbidsCaptureAfterRefundBegins() {
		PaymentIntent intent = authorizedIntent();
		intent.capture(Money.positive(6_000, Currency.BRL), CREATED_AT.plusSeconds(3));

		PaymentIntentDecision excessiveRefund = intent.refund(
			Money.positive(6_001, Currency.BRL),
			CREATED_AT.plusSeconds(4)
		);
		PaymentIntentDecision refund = intent.refund(
			Money.positive(1_000, Currency.BRL),
			CREATED_AT.plusSeconds(4)
		);
		PaymentIntentDecision captureAfterRefund = intent.capture(
			Money.positive(1_000, Currency.BRL),
			CREATED_AT.plusSeconds(5)
		);

		assertThat(excessiveRefund.errorCode())
			.isEqualTo(PaymentIntentErrorCode.REFUND_EXCEEDS_CAPTURED_AMOUNT);
		assertThat(refund.wasApplied()).isTrue();
		assertThat(intent.state()).isEqualTo(PaymentIntentState.PARTIALLY_REFUNDED);
		assertThat(captureAfterRefund.errorCode()).isEqualTo(PaymentIntentErrorCode.INVALID_TRANSITION);
		assertThat(intent.capturedAmount()).isEqualTo(Money.ofMinor(6_000, Currency.BRL));
	}

	@Test
	void preservesMonotonicTimestampsAndVersions() {
		PaymentIntent intent = newIntent();
		intent.startAuthorization(CREATED_AT.plusSeconds(2));

		PaymentIntentDecision decision = intent.authorize(CREATED_AT.plusSeconds(1));

		assertThat(decision.wasApplied()).isFalse();
		assertThat(decision.errorCode()).isEqualTo(PaymentIntentErrorCode.NON_MONOTONIC_TIMESTAMP);
		assertThat(intent.state()).isEqualTo(PaymentIntentState.AUTHORIZATION_PENDING);
		assertThat(intent.version()).isEqualTo(1);
		assertThat(intent.updatedAt()).isEqualTo(CREATED_AT.plusSeconds(2));
	}

	@Test
	void drainsEventsWithoutExposingMutableInternalCollections() {
		PaymentIntent intent = newIntent();
		intent.startAuthorization(CREATED_AT.plusSeconds(1));

		List<PaymentIntentEvent> drained = intent.drainEvents();

		assertThat(drained).hasSize(2);
		assertThat(intent.pendingEvents()).isEmpty();
		assertThatExceptionOfType(UnsupportedOperationException.class)
			.isThrownBy(drained::clear);
	}

	@Test
	void rejectsNonPositiveIntentAmounts() {
		assertThatIllegalArgumentException().isThrownBy(() ->
			PaymentIntent.create(PAYMENT_ID, MERCHANT_ID, Money.zero(Currency.BRL), CREATED_AT)
		);
	}

	@Test
	void deniesCommandsBeforeAnExhaustedVersionCanPartiallyMutateState() {
		PaymentIntent restored = PaymentIntent.restore(
			PAYMENT_ID,
			MERCHANT_ID,
			TOTAL,
			PaymentIntentState.AUTHORIZED,
			Money.zero(Currency.BRL),
			Money.zero(Currency.BRL),
			Long.MAX_VALUE,
			CREATED_AT,
			CREATED_AT.plusSeconds(20)
		);

		PaymentIntentDecision decision = restored.capture(
			Money.positive(1_000, Currency.BRL),
			CREATED_AT.plusSeconds(21)
		);

		assertThat(decision.wasApplied()).isFalse();
		assertThat(decision.errorCode()).isEqualTo(PaymentIntentErrorCode.VERSION_EXHAUSTED);
		assertThat(restored.state()).isEqualTo(PaymentIntentState.AUTHORIZED);
		assertThat(restored.capturedAmount()).isEqualTo(Money.zero(Currency.BRL));
		assertThat(restored.version()).isEqualTo(Long.MAX_VALUE);
		assertThat(restored.pendingEvents()).isEmpty();
	}

	@Test
	void restoresAValidSnapshotWithoutReemittingHistoricalEvents() {
		PaymentIntent restored = PaymentIntent.restore(
			PAYMENT_ID,
			MERCHANT_ID,
			TOTAL,
			PaymentIntentState.PARTIALLY_REFUNDED,
			Money.ofMinor(8_000, Currency.BRL),
			Money.ofMinor(2_000, Currency.BRL),
			9,
			CREATED_AT,
			CREATED_AT.plusSeconds(20)
		);

		assertThat(restored.state()).isEqualTo(PaymentIntentState.PARTIALLY_REFUNDED);
		assertThat(restored.capturedAmount()).isEqualTo(Money.ofMinor(8_000, Currency.BRL));
		assertThat(restored.refundedAmount()).isEqualTo(Money.ofMinor(2_000, Currency.BRL));
		assertThat(restored.version()).isEqualTo(9);
		assertThat(restored.pendingEvents()).isEmpty();
	}

	@Test
	void rejectsSnapshotsWhoseStateAndFinancialTotalsDisagree() {
		assertThatIllegalArgumentException().isThrownBy(() -> PaymentIntent.restore(
			PAYMENT_ID,
			MERCHANT_ID,
			TOTAL,
			PaymentIntentState.CAPTURED,
			Money.ofMinor(9_000, Currency.BRL),
			Money.zero(Currency.BRL),
			4,
			CREATED_AT,
			CREATED_AT.plusSeconds(4)
		)).withMessageContaining(PaymentIntentErrorCode.INVALID_AGGREGATE_STATE.name());
	}

	private static PaymentIntent newIntent() {
		return PaymentIntent.create(PAYMENT_ID, MERCHANT_ID, TOTAL, CREATED_AT);
	}

	private static PaymentIntent authorizedIntent() {
		PaymentIntent intent = newIntent();
		intent.startAuthorization(CREATED_AT.plusSeconds(1));
		intent.authorize(CREATED_AT.plusSeconds(2));
		return intent;
	}
}
