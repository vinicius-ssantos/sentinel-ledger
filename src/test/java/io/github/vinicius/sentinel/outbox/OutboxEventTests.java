package io.github.vinicius.sentinel.outbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxEventTests {

	@Test
	void rejectsABlankAggregateType() {
		assertThatThrownBy(() -> OutboxEvent.enqueue(" ", "id", "payment.captured", "{}", Instant.now()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsABlankAggregateId() {
		assertThatThrownBy(() -> OutboxEvent.enqueue("payment_intent", "", "payment.captured", "{}", Instant.now()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsABlankEventType() {
		assertThatThrownBy(() -> OutboxEvent.enqueue("payment_intent", "id", " ", "{}", Instant.now()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsABlankPayload() {
		assertThatThrownBy(() -> OutboxEvent.enqueue("payment_intent", "id", "payment.captured", " ", Instant.now()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsANullCreatedAt() {
		assertThatThrownBy(() -> OutboxEvent.enqueue("payment_intent", "id", "payment.captured", "{}", null))
			.isInstanceOf(NullPointerException.class);
	}
}
