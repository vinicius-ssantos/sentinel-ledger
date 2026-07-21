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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
	"sentinel.merchant.directory[0].id=11111111-1111-1111-1111-111111111111",
	"sentinel.merchant.directory[0].api-key-id=sentinel-dev-merchant",
	"sentinel.merchant.directory[0].api-key-secret=sentinel-dev-secret"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PaymentIntentAuthorizeApiIntegrationTests {

	private static final String MERCHANT_USERNAME = "sentinel-dev-merchant";
	private static final String MERCHANT_PASSWORD = "sentinel-dev-secret";
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
	void anApprovedAuthorizationReachesAuthorizedSynchronously() throws Exception {
		UUID paymentIntentId = createPaymentIntent("10000");
		pspControls.programNextAttempt(
			new PaymentIntentId(paymentIntentId),
			new PspAuthorizationResult.Approved(new PspProviderReference("provider-ref-approved")),
			new PspAuthorizationResult.Approved(new PspProviderReference("provider-ref-approved"))
		);

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/authorize")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("AUTHORIZED"));
	}

	@Test
	void aDeclinedAuthorizationReachesDeclinedSynchronously() throws Exception {
		UUID paymentIntentId = createPaymentIntent("10000");
		PspAuthorizationResult declined = new PspAuthorizationResult.Declined("INSUFFICIENT_FUNDS");
		pspControls.programNextAttempt(new PaymentIntentId(paymentIntentId), declined, declined);

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/authorize")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("DECLINED"));
	}

	@Test
	void aPermanentFailureReachesFailedSynchronously() throws Exception {
		UUID paymentIntentId = createPaymentIntent("10000");
		PspAuthorizationResult failure = new PspAuthorizationResult.PermanentFailure("malformed-response");
		pspControls.programNextAttempt(new PaymentIntentId(paymentIntentId), failure, failure);

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/authorize")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("FAILED"));
	}

	@Test
	void aTimeoutBeforeProcessingNeverImpliesDeclineAndReturnsAccepted() throws Exception {
		UUID paymentIntentId = createPaymentIntent("10000");
		PspAuthorizationResult unknown = new PspAuthorizationResult.Unknown();
		pspControls.programNextAttempt(new PaymentIntentId(paymentIntentId), unknown, unknown);

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/authorize")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey()))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.state").value("AUTHORIZATION_UNKNOWN"));
	}

	@Test
	void aTimeoutAfterProcessingIsRecoveredByRetryingWithTheSameKeyWithoutASecondAuthorization() throws Exception {
		UUID paymentIntentId = createPaymentIntent("10000");
		PspAuthorizationResult trueOutcome = new PspAuthorizationResult.Approved(new PspProviderReference("provider-ref-recovered"));
		pspControls.programNextAttempt(new PaymentIntentId(paymentIntentId), new PspAuthorizationResult.Unknown(), trueOutcome);
		String idempotencyKey = newIdempotencyKey();

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/authorize")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", idempotencyKey))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.state").value("AUTHORIZATION_UNKNOWN"));

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/authorize")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", idempotencyKey))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("AUTHORIZED"));

		mockMvc.perform(get("/api/v1/payment-intents/" + paymentIntentId)
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD)))
			.andExpect(jsonPath("$.state").value("AUTHORIZED"));
	}

	@Test
	void retryingWithTheSameKeyAfterAFinalOutcomeReplaysTheOriginalResponse() throws Exception {
		UUID paymentIntentId = createPaymentIntent("10000");
		pspControls.programNextAttempt(
			new PaymentIntentId(paymentIntentId),
			new PspAuthorizationResult.Approved(new PspProviderReference("provider-ref-replay")),
			new PspAuthorizationResult.Approved(new PspProviderReference("provider-ref-replay"))
		);
		String idempotencyKey = newIdempotencyKey();

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/authorize")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", idempotencyKey))
			.andExpect(status().isOk())
			.andExpect(header().doesNotExist("Idempotent-Replayed"));

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/authorize")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", idempotencyKey))
			.andExpect(status().isOk())
			.andExpect(header().string("Idempotent-Replayed", "true"))
			.andExpect(jsonPath("$.state").value("AUTHORIZED"));
	}

	@Test
	void authorizingAPaymentIntentThatIsAlreadyAuthorizedIsRejected() throws Exception {
		UUID paymentIntentId = createPaymentIntent("10000");
		pspControls.programNextAttempt(
			new PaymentIntentId(paymentIntentId),
			new PspAuthorizationResult.Approved(new PspProviderReference("provider-ref-first")),
			new PspAuthorizationResult.Approved(new PspProviderReference("provider-ref-first"))
		);
		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/authorize")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey()))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/authorize")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_PAYMENT_TRANSITION"));
	}

	@Test
	void authorizingAnUnknownPaymentIntentReturnsNotFound() throws Exception {
		mockMvc.perform(post("/api/v1/payment-intents/" + UUID.randomUUID() + "/authorize")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PAYMENT_INTENT_NOT_FOUND"));
	}

	@Test
	void authorizingWithoutAnIdempotencyKeyIsRejected() throws Exception {
		UUID paymentIntentId = createPaymentIntent("10000");

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/authorize")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
	}

	private UUID createPaymentIntent(String amountInMinorUnits) throws Exception {
		MvcResult creation = mockMvc.perform(post("/api/v1/payment-intents")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"" + amountInMinorUnits + "\",\"currency\":\"BRL\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		String location = creation.getResponse().getHeader("Location");
		Matcher matcher = LOCATION_ID.matcher(location);
		assertThat(matcher.find()).isTrue();
		return UUID.fromString(matcher.group(1));
	}

	private static String newIdempotencyKey() {
		return UUID.randomUUID().toString();
	}
}
