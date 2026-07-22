package io.github.vinicius.sentinel.payments;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.integration.psp.SimulatedPspControls;
import io.github.vinicius.sentinel.ledger.AccountId;
import io.github.vinicius.sentinel.ledger.LedgerProjectionPort;
import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
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
class PaymentIntentRefundApiIntegrationTests {

	private static final String MERCHANT_USERNAME = "sentinel-dev-merchant";
	private static final String MERCHANT_PASSWORD = "sentinel-dev-secret";
	private static final UUID MERCHANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final AccountId PAYABLE = AccountId.merchantPayable(MERCHANT_ID, Currency.BRL);
	private static final Pattern LOCATION_ID = Pattern.compile("/api/v1/payment-intents/([0-9a-fA-F-]{36})$");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SimulatedPspControls pspControls;

	@Autowired
	private LedgerProjectionPort ledgerProjectionPort;

	@BeforeEach
	void resetPsp() {
		pspControls.reset();
	}

	@Test
	void aFullRefundPostsOneBalancedCompensatingLedgerTransactionAndReachesRefunded() throws Exception {
		UUID paymentIntentId = createAuthorizeAndCapture("10000", "10000");
		long payableBefore = payableBalanceMinor();

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/refunds")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"10000\",\"currency\":\"BRL\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("REFUNDED"))
			.andExpect(jsonPath("$.refundedAmountInMinorUnits").value("10000"));

		assertThat(payableDeltaSince(payableBefore)).isEqualTo(-10_000L);
	}

	@Test
	void partialRefundsCanReachButNeverExceedTheCapturedAmount() throws Exception {
		UUID paymentIntentId = createAuthorizeAndCapture("10000", "10000");

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/refunds")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"3000\",\"currency\":\"BRL\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("PARTIALLY_REFUNDED"))
			.andExpect(jsonPath("$.refundedAmountInMinorUnits").value("3000"));

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/refunds")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"7000\",\"currency\":\"BRL\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("REFUNDED"))
			.andExpect(jsonPath("$.refundedAmountInMinorUnits").value("10000"));
	}

	@Test
	void aRefundExceedingTheRefundableAmountIsRejected() throws Exception {
		UUID paymentIntentId = createAuthorizeAndCapture("10000", "4000");

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/refunds")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"4001\",\"currency\":\"BRL\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REFUND_LIMIT_EXCEEDED"));
	}

	@Test
	void retryingWithTheSameKeyReplaysTheOriginalRefundWithoutPostingASecondLedgerTransaction() throws Exception {
		UUID paymentIntentId = createAuthorizeAndCapture("10000", "10000");
		String idempotencyKey = newIdempotencyKey();
		long payableBefore = payableBalanceMinor();

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/refunds")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"3000\",\"currency\":\"BRL\"}"))
			.andExpect(status().isOk())
			.andExpect(header().doesNotExist("Idempotent-Replayed"));

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/refunds")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"3000\",\"currency\":\"BRL\"}"))
			.andExpect(status().isOk())
			.andExpect(header().string("Idempotent-Replayed", "true"))
			.andExpect(jsonPath("$.refundedAmountInMinorUnits").value("3000"));

		assertThat(payableDeltaSince(payableBefore)).isEqualTo(-3_000L);
	}

	@Test
	void refundingAPaymentIntentThatWasNeverCapturedIsRejected() throws Exception {
		UUID paymentIntentId = createAndAuthorize("10000");

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/refunds")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"5000\",\"currency\":\"BRL\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_PAYMENT_TRANSITION"));
	}

	@Test
	void refundingAnUnknownPaymentIntentReturnsNotFound() throws Exception {
		mockMvc.perform(post("/api/v1/payment-intents/" + UUID.randomUUID() + "/refunds")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"5000\",\"currency\":\"BRL\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PAYMENT_INTENT_NOT_FOUND"));
	}

	@Test
	void refundingWithoutAnIdempotencyKeyIsRejected() throws Exception {
		UUID paymentIntentId = createAuthorizeAndCapture("10000", "10000");

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/refunds")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"5000\",\"currency\":\"BRL\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
	}

	@Test
	void capturingAfterAFirstSuccessfulRefundIsRejected() throws Exception {
		UUID paymentIntentId = createAuthorizeAndCapture("10000", "4000");

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/refunds")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"1000\",\"currency\":\"BRL\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("PARTIALLY_REFUNDED"));

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/captures")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"1000\",\"currency\":\"BRL\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_PAYMENT_TRANSITION"));
	}

	private UUID createAuthorizeAndCapture(String authorizeAmount, String captureAmount) throws Exception {
		UUID paymentIntentId = createAndAuthorize(authorizeAmount);
		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/captures")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"" + captureAmount + "\",\"currency\":\"BRL\"}"))
			.andExpect(status().isOk());
		return paymentIntentId;
	}

	private UUID createAndAuthorize(String amountInMinorUnits) throws Exception {
		UUID paymentIntentId = create(amountInMinorUnits);
		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/authorize")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", newIdempotencyKey()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("AUTHORIZED"));
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

	private long payableBalanceMinor() {
		return ledgerProjectionPort.currentBalance(PAYABLE).map(Money::amountInMinorUnits).orElse(0L);
	}

	private long payableDeltaSince(long before) {
		return payableBalanceMinor() - before;
	}

	private static String newIdempotencyKey() {
		return UUID.randomUUID().toString();
	}
}
