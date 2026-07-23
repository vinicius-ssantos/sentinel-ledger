package io.github.vinicius.sentinel.webhooks;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookDeliveryRequestTests {

	private static final WebhookDeliveryId ID = new WebhookDeliveryId(UUID.randomUUID());

	@Test
	void rejectsABlankAggregateType() {
		assertThatThrownBy(() -> new WebhookDeliveryRequest(ID, " ", "id", "payment.captured", "{}"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsABlankAggregateId() {
		assertThatThrownBy(() -> new WebhookDeliveryRequest(ID, "payment_intent", "", "payment.captured", "{}"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsABlankEventType() {
		assertThatThrownBy(() -> new WebhookDeliveryRequest(ID, "payment_intent", "id", " ", "{}"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsABlankPayload() {
		assertThatThrownBy(() -> new WebhookDeliveryRequest(ID, "payment_intent", "id", "payment.captured", " "))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsANullId() {
		assertThatThrownBy(() -> new WebhookDeliveryRequest(null, "payment_intent", "id", "payment.captured", "{}"))
			.isInstanceOf(NullPointerException.class);
	}
}
