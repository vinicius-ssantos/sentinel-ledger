package io.github.vinicius.sentinel.payments;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.integration.psp.SimulatedPspControls;
import io.github.vinicius.sentinel.outbox.OutboxEventStatus;
import io.github.vinicius.sentinel.outbox.OutboxQueryPort;
import io.github.vinicius.sentinel.outbox.OutboxRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves OUT-001 end to end: capture and refund enqueue their outbox publication intent in the same local
 * transaction as the ledger posting, not as an afterthought.
 */
@SpringBootTest(properties = {
	"sentinel.merchant.directory[0].id=11111111-1111-1111-1111-111111111111",
	"sentinel.merchant.directory[0].api-key-id=sentinel-dev-merchant",
	"sentinel.merchant.directory[0].api-key-secret=sentinel-dev-secret"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CaptureAndRefundOutboxIntegrationTests {

	private static final String MERCHANT_USERNAME = "sentinel-dev-merchant";
	private static final String MERCHANT_PASSWORD = "sentinel-dev-secret";
	private static final Pattern LOCATION_ID = Pattern.compile("/api/v1/payment-intents/([0-9a-fA-F-]{36})$");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SimulatedPspControls pspControls;

	@Autowired
	private OutboxQueryPort outboxQueryPort;

	@BeforeEach
	void resetPsp() {
		pspControls.reset();
	}

	@Test
	void aSuccessfulCaptureEnqueuesExactlyOnePendingOutboxEvent() throws Exception {
		UUID paymentIntentId = createAndAuthorize("10000");

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/captures")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"10000\",\"currency\":\"BRL\"}"))
			.andExpect(status().isOk());

		List<OutboxRecord> forAggregate = outboxEventsFor(paymentIntentId);
		assertThat(forAggregate).hasSize(1);
		OutboxRecord record = forAggregate.getFirst();
		assertThat(record.eventType()).isEqualTo("payment.captured");
		assertThat(record.aggregateType()).isEqualTo("payment_intent");
		assertThat(record.status()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(record.payload()).contains(paymentIntentId.toString()).contains("10000");
	}

	@Test
	void aRejectedCaptureEnqueuesNoOutboxEvent() throws Exception {
		UUID paymentIntentId = createAndAuthorize("10000");

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/captures")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"10001\",\"currency\":\"BRL\"}"))
			.andExpect(status().isConflict());

		assertThat(outboxEventsFor(paymentIntentId)).isEmpty();
	}

	@Test
	void aSuccessfulRefundEnqueuesExactlyOnePendingOutboxEvent() throws Exception {
		UUID paymentIntentId = createAndAuthorize("10000");
		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/captures")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"10000\",\"currency\":\"BRL\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/refunds")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"3000\",\"currency\":\"BRL\"}"))
			.andExpect(status().isOk());

		List<OutboxRecord> refundEvents = outboxEventsFor(paymentIntentId).stream()
			.filter(r -> r.eventType().equals("payment.refunded"))
			.toList();
		assertThat(refundEvents).hasSize(1);
		assertThat(refundEvents.getFirst().payload()).contains("3000");
	}

	private List<OutboxRecord> outboxEventsFor(UUID paymentIntentId) {
		return outboxQueryPort.findByStatus(OutboxEventStatus.PENDING).stream()
			.filter(r -> r.aggregateId().equals(paymentIntentId.toString()))
			.toList();
	}

	private UUID createAndAuthorize(String amountInMinorUnits) throws Exception {
		UUID paymentIntentId = create(amountInMinorUnits);
		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/authorize")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey()))
			.andExpect(status().isOk());
		return paymentIntentId;
	}

	private UUID create(String amountInMinorUnits) throws Exception {
		MvcResult creation = mockMvc.perform(post("/api/v1/payment-intents")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"" + amountInMinorUnits + "\",\"currency\":\"BRL\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		Matcher matcher = LOCATION_ID.matcher(creation.getResponse().getHeader("Location"));
		assertThat(matcher.find()).isTrue();
		return UUID.fromString(matcher.group(1));
	}

	private static String newIdempotencyKey() {
		return UUID.randomUUID().toString();
	}
}
