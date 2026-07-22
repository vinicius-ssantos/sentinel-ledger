package io.github.vinicius.sentinel.payments;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
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

import java.util.ArrayList;
import java.util.List;
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
	"sentinel.merchant.directory[1].id=22222222-2222-2222-2222-222222222222",
	"sentinel.merchant.directory[1].api-key-id=sentinel-dev-merchant-2",
	"sentinel.merchant.directory[1].api-key-secret=sentinel-dev-secret-2"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LedgerAccountEntriesApiIntegrationTests {

	private static final String MERCHANT_USERNAME = "sentinel-dev-merchant";
	private static final String MERCHANT_PASSWORD = "sentinel-dev-secret";
	private static final String MERCHANT_ID = "11111111-1111-1111-1111-111111111111";
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
	void pagesThroughAMerchantsOwnAccountEntriesWithoutSkippingOrDuplicating() throws Exception {
		for (int i = 0; i < 3; i++) {
			UUID paymentIntentId = createAndAuthorize("1000");
			mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/captures")
					.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
					.header("Idempotency-Key", UUID.randomUUID().toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"amountInMinorUnits\":\"1000\",\"currency\":\"BRL\"}"))
				.andExpect(status().isOk());
		}

		String accountId = "merchant-payable:" + MERCHANT_ID + ":BRL";
		List<String> seenReferences = new ArrayList<>();
		String cursor = null;
		int pageCount = 0;
		do {
			var request = get("/api/v1/ledger/accounts/{accountId}/entries", accountId)
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.param("limit", "1");
			if (cursor != null) {
				request = request.param("cursor", cursor);
			}
			MvcResult result = mockMvc.perform(request)
				.andExpect(status().isOk())
				.andReturn();
			String body = result.getResponse().getContentAsString();
			DocumentContext json = JsonPath.parse(body);
			List<String> pageReferences = json.read("$.entries[*].businessEffectReference");
			seenReferences.addAll(pageReferences);
			cursor = json.read("$.nextCursor");
			pageCount++;
			assertThat(pageCount).isLessThanOrEqualTo(10);
		} while (cursor != null);

		assertThat(seenReferences).hasSizeGreaterThanOrEqualTo(3);
		assertThat(seenReferences).doesNotHaveDuplicates();
	}

	@Test
	void aMerchantCannotBrowseAnotherMerchantsAccount() throws Exception {
		String accountId = "merchant-payable:" + MERCHANT_ID + ":BRL";

		mockMvc.perform(get("/api/v1/ledger/accounts/{accountId}/entries", accountId)
				.with(httpBasic(OTHER_MERCHANT_USERNAME, OTHER_MERCHANT_PASSWORD)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("LEDGER_ACCOUNT_NOT_FOUND"));
	}

	@Test
	void theSharedPspClearingAccountIsNotBrowsableByAMerchant() throws Exception {
		mockMvc.perform(get("/api/v1/ledger/accounts/{accountId}/entries", "psp-clearing-receivable:BRL")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("LEDGER_ACCOUNT_NOT_FOUND"));
	}

	@Test
	void aGarbageCursorIsRejected() throws Exception {
		String accountId = "merchant-payable:" + MERCHANT_ID + ":BRL";

		mockMvc.perform(get("/api/v1/ledger/accounts/{accountId}/entries", accountId)
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.param("cursor", "not-a-real-cursor"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_LEDGER_CURSOR"));
	}

	private UUID createAndAuthorize(String amountInMinorUnits) throws Exception {
		MvcResult creation = mockMvc.perform(post("/api/v1/payment-intents")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"" + amountInMinorUnits + "\",\"currency\":\"BRL\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		Matcher matcher = LOCATION_ID.matcher(creation.getResponse().getHeader("Location"));
		assertThat(matcher.find()).isTrue();
		UUID paymentIntentId = UUID.fromString(matcher.group(1));

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/authorize")
				.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
				.header("Idempotency-Key", UUID.randomUUID().toString()))
			.andExpect(status().isOk());

		return paymentIntentId;
	}
}
