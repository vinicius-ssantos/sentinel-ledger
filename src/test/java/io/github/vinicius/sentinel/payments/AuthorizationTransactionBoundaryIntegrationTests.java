package io.github.vinicius.sentinel.payments;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, rather than just structurally implying, that the PSP is invoked without an active database transaction:
 * a recording port asserts {@link TransactionSynchronizationManager#isActualTransactionActive()} is false when
 * called, failing the test immediately if a future change accidentally widens the transaction boundary.
 */
@SpringBootTest(properties = {
	"sentinel.merchant.directory[0].id=11111111-1111-1111-1111-111111111111",
	"sentinel.merchant.directory[0].api-key-id=sentinel-dev-merchant",
	"sentinel.merchant.directory[0].api-key-secret=sentinel-dev-secret"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthorizationTransactionBoundaryIntegrationTests.RecordingPspConfiguration.class})
class AuthorizationTransactionBoundaryIntegrationTests {

	private static final Pattern LOCATION_ID = Pattern.compile("/api/v1/payment-intents/([0-9a-fA-F-]{36})$");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RecordingPort recordingPort;

	@Test
	void noDatabaseTransactionIsActiveWhileTheAuthorizeCallIsInFlight() throws Exception {
		recordingPort.reset();

		MvcResult creation = mockMvc.perform(post("/api/v1/payment-intents")
				.with(httpBasic("sentinel-dev-merchant", "sentinel-dev-secret"))
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountInMinorUnits\":\"5000\",\"currency\":\"BRL\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		Matcher matcher = LOCATION_ID.matcher(creation.getResponse().getHeader("Location"));
		assertThat(matcher.find()).isTrue();
		String paymentIntentId = matcher.group(1);

		mockMvc.perform(post("/api/v1/payment-intents/" + paymentIntentId + "/authorize")
				.with(httpBasic("sentinel-dev-merchant", "sentinel-dev-secret"))
				.header("Idempotency-Key", UUID.randomUUID().toString()))
			.andExpect(status().isOk());

		assertThat(recordingPort.wasCalled()).isTrue();
		assertThat(recordingPort.transactionWasActiveDuringCall()).isFalse();
	}

	@TestConfiguration
	static class RecordingPspConfiguration {

		@Bean
		@Primary
		RecordingPort recordingPspAuthorizationPort() {
			return new RecordingPort();
		}
	}

	static class RecordingPort implements PspAuthorizationPort {

		private final AtomicBoolean called = new AtomicBoolean(false);
		private final AtomicBoolean transactionActive = new AtomicBoolean(false);

		void reset() {
			called.set(false);
			transactionActive.set(false);
		}

		boolean wasCalled() {
			return called.get();
		}

		boolean transactionWasActiveDuringCall() {
			return transactionActive.get();
		}

		@Override
		public PspAuthorizationResult authorize(PspAuthorizationRequest request) {
			called.set(true);
			if (TransactionSynchronizationManager.isActualTransactionActive()) {
				transactionActive.set(true);
			}
			return new PspAuthorizationResult.Approved(new PspProviderReference("recording-ref"));
		}

		@Override
		public PspAuthorizationResult checkStatus(PspAttemptId attemptId) {
			if (TransactionSynchronizationManager.isActualTransactionActive()) {
				transactionActive.set(true);
			}
			return new PspAuthorizationResult.Unknown();
		}
	}
}
