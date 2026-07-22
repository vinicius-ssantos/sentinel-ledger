package io.github.vinicius.sentinel.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditEventTests {

	private static final AuditActor MERCHANT = AuditActor.merchant(UUID.randomUUID().toString());

	@Test
	void rejectsABlankAction() {
		assertThatThrownBy(() -> AuditEvent.record(
			MERCHANT, "  ", "payment_intent", "id", "correlation", Map.of(), Instant.now()
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsABlankResourceType() {
		assertThatThrownBy(() -> AuditEvent.record(
			MERCHANT, "payment-intent.create", " ", "id", "correlation", Map.of(), Instant.now()
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsABlankResourceId() {
		assertThatThrownBy(() -> AuditEvent.record(
			MERCHANT, "payment-intent.create", "payment_intent", "", "correlation", Map.of(), Instant.now()
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsABlankCorrelationId() {
		assertThatThrownBy(() -> AuditEvent.record(
			MERCHANT, "payment-intent.create", "payment_intent", "id", "", Map.of(), Instant.now()
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsABlankActorId() {
		assertThatThrownBy(() -> AuditActor.merchant(" ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsANullMetadataValue() {
		assertThatThrownBy(() -> AuditEvent.record(
			MERCHANT, "payment-intent.create", "payment_intent", "id", "correlation", null, Instant.now()
		)).isInstanceOf(NullPointerException.class);
	}
}
