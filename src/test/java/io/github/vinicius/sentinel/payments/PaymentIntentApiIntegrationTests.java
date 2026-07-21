package io.github.vinicius.sentinel.payments;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

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
	"sentinel.merchant.directory[0].api-key-secret=sentinel-dev-secret",
	"sentinel.merchant.directory[1].id=22222222-2222-2222-2222-222222222222",
	"sentinel.merchant.directory[1].api-key-id=sentinel-dev-other-merchant",
	"sentinel.merchant.directory[1].api-key-secret=sentinel-dev-other-secret"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PaymentIntentApiIntegrationTests {

	private static final String MERCHANT_USERNAME = "sentinel-dev-merchant";
	private static final String MERCHANT_PASSWORD = "sentinel-dev-secret";
	private static final String OTHER_MERCHANT_USERNAME = "sentinel-dev-other-merchant";
	private static final String OTHER_MERCHANT_PASSWORD = "sentinel-dev-other-secret";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void createsAndReadsBackAPaymentIntentForTheAuthenticatedMerchant() throws Exception {
		MvcResult creation = mockMvc.perform(post("/api/v1/payment-intents")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"12500\",\"currency\":\"BRL\"}"))
			.andExpect(status().isCreated())
			.andExpect(header().exists("Location"))
			.andExpect(jsonPath("$.state").value("CREATED"))
			.andExpect(jsonPath("$.amountInMinorUnits").value("12500"))
			.andExpect(jsonPath("$.currency").value("BRL"))
			.andExpect(jsonPath("$.capturedAmountInMinorUnits").value("0"))
			.andExpect(jsonPath("$.refundedAmountInMinorUnits").value("0"))
			.andReturn();

		String location = creation.getResponse().getHeader("Location");
		assertThat(location).isNotNull();

		mockMvc.perform(get(location).with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("CREATED"))
			.andExpect(jsonPath("$.amountInMinorUnits").value("12500"));
	}

	@Test
	void hidesAPaymentIntentFromAMerchantThatDoesNotOwnIt() throws Exception {
		MvcResult creation = mockMvc.perform(post("/api/v1/payment-intents")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"500\",\"currency\":\"BRL\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		String location = creation.getResponse().getHeader("Location");

		mockMvc.perform(get(location).with(httpBasic(OTHER_MERCHANT_USERNAME, OTHER_MERCHANT_PASSWORD)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PAYMENT_INTENT_NOT_FOUND"));
	}

	@Test
	void returnsNotFoundForAnUnknownPaymentIntent() throws Exception {
		mockMvc.perform(get("/api/v1/payment-intents/" + UUID.randomUUID())
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PAYMENT_INTENT_NOT_FOUND"));
	}

	@Test
	void rejectsAMissingCurrency() throws Exception {
		mockMvc.perform(post("/api/v1/payment-intents")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"500\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void rejectsANonPositiveAmount() throws Exception {
		mockMvc.perform(post("/api/v1/payment-intents")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"0\",\"currency\":\"BRL\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void rejectsAMalformedAmount() throws Exception {
		mockMvc.perform(post("/api/v1/payment-intents")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"12.50\",\"currency\":\"BRL\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void rejectsAnUnsupportedCurrency() throws Exception {
		mockMvc.perform(post("/api/v1/payment-intents")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"500\",\"currency\":\"USD\"}"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
	}

	@Test
	void rejectsAnUnauthenticatedRequest() throws Exception {
		mockMvc.perform(post("/api/v1/payment-intents")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"500\",\"currency\":\"BRL\"}"))
			.andExpect(status().isUnauthorized());
	}
}
