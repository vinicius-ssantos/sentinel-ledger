package io.github.vinicius.sentinel.payments;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.integration.psp.SimulatedPspControls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
	"sentinel.merchant.directory[0].id=11111111-1111-1111-1111-111111111111",
	"sentinel.merchant.directory[0].api-key-id=sentinel-dev-merchant",
	"sentinel.merchant.directory[0].api-key-secret=sentinel-dev-secret",
	"sentinel.merchant.directory[1].id=22222222-2222-2222-2222-222222222222",
	"sentinel.merchant.directory[1].api-key-id=sentinel-dev-merchant-2",
	"sentinel.merchant.directory[1].api-key-secret=sentinel-dev-secret-2"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PaymentIntentTimelineApiIntegrationTests {

	private static final String MERCHANT_USERNAME = "sentinel-dev-merchant";
	private static final String MERCHANT_PASSWORD = "sentinel-dev-secret";
	private static final String OTHER_MERCHANT_USERNAME = "sentinel-dev-merchant-2";
	private static final String OTHER_MERCHANT_PASSWORD = "sentinel-dev-secret-2";
	private static final Pattern LOCATION_ID = Pattern.compile("/api/v1/payment-intents/([0-9a-fA-F-]{36})$");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SimulatedPspControls pspControls;

	@BeforeEach
	void resetPsp() {
		pspControls.reset();
	}

	@Test
	void aTimelineCorrelatesAuditProviderAndLedgerEvidenceInOrder() throws Exception {
		UUID paymentIntentId = create("10000");
		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/authorize")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey()))
			.andExpect(status().isOk());
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
				.content("{\"amountInMinorUnits\":\"4000\",\"currency\":\"BRL\"}"))
			.andExpect(status().isOk());

		MvcResult result = mockMvc.perform(get("/api/v1/payment-intents/" + paymentIntentId + "/timeline")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(6))))
			.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains("\"AUDIT_EVENT\"");
		assertThat(body).contains("\"PROVIDER_RESULT\"");
		assertThat(body).contains("\"LEDGER_TRANSACTION\"");
		assertThat(body).contains("payment-intent.create");
		assertThat(body).contains("payment-intent.authorize");
		assertThat(body).contains("payment-intent.capture");
		assertThat(body).contains("payment-intent.refund");
		assertThat(body).contains("ledger.capture");
		assertThat(body).contains("ledger.refund");
	}

	@Test
	void aFreshlyCreatedIntentsTimelineContainsOnlyItsCreateEvent() throws Exception {
		UUID paymentIntentId = create("5000");

		mockMvc.perform(get("/api/v1/payment-intents/" + paymentIntentId + "/timeline")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void anotherMerchantCannotReadThisTimeline() throws Exception {
		UUID paymentIntentId = create("5000");

		mockMvc.perform(get("/api/v1/payment-intents/" + paymentIntentId + "/timeline")
				.with(httpBasic(OTHER_MERCHANT_USERNAME, OTHER_MERCHANT_PASSWORD)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PAYMENT_INTENT_NOT_FOUND"));
	}

	@Test
	void anUnknownPaymentIntentReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/payment-intents/" + UUID.randomUUID() + "/timeline")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PAYMENT_INTENT_NOT_FOUND"));
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
