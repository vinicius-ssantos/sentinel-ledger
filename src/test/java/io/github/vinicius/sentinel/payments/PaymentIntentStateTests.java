package io.github.vinicius.sentinel.payments;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static io.github.vinicius.sentinel.payments.PaymentIntentState.*;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentIntentStateTests {

	private static final Set<Transition> ALLOWED = Set.of(
		new Transition(CREATED, AUTHORIZATION_PENDING),
		new Transition(CREATED, CANCELLED),
		new Transition(AUTHORIZATION_PENDING, AUTHORIZATION_UNKNOWN),
		new Transition(AUTHORIZATION_PENDING, AUTHORIZED),
		new Transition(AUTHORIZATION_PENDING, DECLINED),
		new Transition(AUTHORIZATION_PENDING, FAILED),
		new Transition(AUTHORIZATION_UNKNOWN, AUTHORIZED),
		new Transition(AUTHORIZATION_UNKNOWN, DECLINED),
		new Transition(AUTHORIZATION_UNKNOWN, FAILED),
		new Transition(AUTHORIZED, PARTIALLY_CAPTURED),
		new Transition(AUTHORIZED, CAPTURED),
		new Transition(AUTHORIZED, CANCELLED),
		new Transition(PARTIALLY_CAPTURED, PARTIALLY_CAPTURED),
		new Transition(PARTIALLY_CAPTURED, CAPTURED),
		new Transition(PARTIALLY_CAPTURED, PARTIALLY_REFUNDED),
		new Transition(PARTIALLY_CAPTURED, REFUNDED),
		new Transition(CAPTURED, PARTIALLY_REFUNDED),
		new Transition(CAPTURED, REFUNDED),
		new Transition(PARTIALLY_REFUNDED, PARTIALLY_REFUNDED),
		new Transition(PARTIALLY_REFUNDED, REFUNDED)
	);

	@ParameterizedTest(name = "{0} -> {1} is allowed")
	@MethodSource("allowedTransitions")
	void allowsEveryNormativeTransition(PaymentIntentState source, PaymentIntentState target) {
		assertThat(source.canTransitionTo(target)).isTrue();
	}

	@ParameterizedTest(name = "{0} -> {1} is rejected")
	@MethodSource("rejectedTransitions")
	void rejectsEveryNonNormativeTransition(PaymentIntentState source, PaymentIntentState target) {
		assertThat(source.canTransitionTo(target)).isFalse();
	}

	@Test
	void identifiesOnlyStatesWithoutOutgoingTransitionsAsTerminal() {
		assertThat(Stream.of(PaymentIntentState.values())
			.filter(PaymentIntentState::isTerminal))
			.containsExactlyInAnyOrder(REFUNDED, DECLINED, FAILED, CANCELLED);
	}

	static Stream<Arguments> allowedTransitions() {
		return ALLOWED.stream().map(transition -> Arguments.of(transition.source(), transition.target()));
	}

	static Stream<Arguments> rejectedTransitions() {
		return Stream.of(PaymentIntentState.values())
			.flatMap(source -> Stream.of(PaymentIntentState.values())
				.map(target -> new Transition(source, target)))
			.filter(transition -> !ALLOWED.contains(transition))
			.map(transition -> Arguments.of(transition.source(), transition.target()));
	}

	private record Transition(PaymentIntentState source, PaymentIntentState target) {}
}
