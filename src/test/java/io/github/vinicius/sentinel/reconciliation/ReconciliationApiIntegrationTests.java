package io.github.vinicius.sentinel.reconciliation;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.integration.psp.SimulatedPspControls;
import io.github.vinicius.sentinel.payments.PspAuthorizationResult;
import io.github.vinicius.sentinel.payments.PspProviderReference;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
	"sentinel.merchant.directory[0].id=11111111-1111-1111-1111-111111111111",
	"sentinel.merchant.directory[0].api-key-id=sentinel-dev-merchant",
	"sentinel.merchant.directory[0].api-key-secret=sentinel-dev-secret",
	"sentinel.operator.directory[0].id=33333333-3333-3333-3333-333333333333",
	"sentinel.operator.directory[0].api-key-id=sentinel-dev-operator",
	"sentinel.operator.directory[0].api-key-secret=sentinel-dev-operator-secret"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReconciliationApiIntegrationTests {

	private static final String MERCHANT_USERNAME = "sentinel-dev-merchant";
	private static final String MERCHANT_PASSWORD = "sentinel-dev-secret";
	private static final String OPERATOR_USERNAME = "sentinel-dev-operator";
	private static final String OPERATOR_PASSWORD = "sentinel-dev-operator-secret";
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
	void anonymousRequestsAreRejected() throws Exception {
		mockMvc.perform(get("/api/v1/reconciliation/cases")).andExpect(status().isUnauthorized());
	}

	@Test
	void aMerchantCredentialCannotAccessThePrivilegedOperatorApi() throws Exception {
		mockMvc.perform(get("/api/v1/reconciliation/cases").with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD)))
			.andExpect(status().isForbidden());
	}

	@Test
	void anOperatorCanRunAnOnDemandCheckForAPaymentIntentWithNoEvidence() throws Exception {
		UUID paymentIntentId = create("5000");

		mockMvc.perform(post("/api/v1/reconciliation/checks/payment-intents/" + paymentIntentId)
				.with(httpBasic(OPERATOR_USERNAME, OPERATOR_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome").value("NO_EVIDENCE"));
	}

	@Test
	void anUnknownPaymentIntentCheckReturnsNotFound() throws Exception {
		mockMvc.perform(post("/api/v1/reconciliation/checks/payment-intents/" + UUID.randomUUID())
				.with(httpBasic(OPERATOR_USERNAME, OPERATOR_PASSWORD)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PAYMENT_INTENT_NOT_FOUND"));
	}

	@Test
	void detectingAndResolvingAMismatchEndToEnd() throws Exception {
		UUID paymentIntentId = create("6000");
		pspControls.programNextAttempt(
			new io.github.vinicius.sentinel.payments.PaymentIntentId(paymentIntentId),
			new PspAuthorizationResult.Approved(new PspProviderReference("ref-api")),
			new PspAuthorizationResult.Declined("later_declined")
		);
		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/authorize")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", UUID.randomUUID().toString()))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/captures")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"6000\",\"currency\":\"BRL\"}"))
			.andExpect(status().isOk());

		MvcResult checkResult = mockMvc.perform(post("/api/v1/reconciliation/checks/payment-intents/" + paymentIntentId)
				.with(httpBasic(OPERATOR_USERNAME, OPERATOR_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome").value("MISMATCH_DETECTED"))
			.andExpect(jsonPath("$.reconciliationCase.severity").value("HIGH"))
			.andExpect(jsonPath("$.reconciliationCase.status").value("OPEN"))
			.andReturn();
		String caseId = com.jayway.jsonpath.JsonPath.read(checkResult.getResponse().getContentAsString(), "$.reconciliationCase.id");

		mockMvc.perform(post("/api/v1/reconciliation/cases/" + caseId + "/resolve")
				.with(httpBasic(OPERATOR_USERNAME, OPERATOR_PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"x\",\"action\":\"COMPENSATE\",\"confirm\":false}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/reconciliation/cases/" + caseId + "/resolve")
				.with(httpBasic(OPERATOR_USERNAME, OPERATOR_PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"\",\"action\":\"COMPENSATE\",\"confirm\":true}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/reconciliation/cases/" + caseId + "/resolve")
				.with(httpBasic(OPERATOR_USERNAME, OPERATOR_PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"confirmed with provider support\",\"action\":\"COMPENSATE\",\"confirm\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RESOLVED"))
			.andExpect(jsonPath("$.resolution.action").value("COMPENSATE"))
			.andExpect(jsonPath("$.resolution.compensatingTransactionReference").exists());

		mockMvc.perform(get("/api/v1/reconciliation/cases").param("status", "OPEN").with(httpBasic(OPERATOR_USERNAME, OPERATOR_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + caseId + "')]").isEmpty());
	}

	private UUID create(String amountInMinorUnits) throws Exception {
		MvcResult creation = mockMvc.perform(post("/api/v1/payment-intents")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"" + amountInMinorUnits + "\",\"currency\":\"BRL\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		Matcher matcher = LOCATION_ID.matcher(creation.getResponse().getHeader("Location"));
		assertThat(matcher.find()).isTrue();
		return UUID.fromString(matcher.group(1));
	}
}
