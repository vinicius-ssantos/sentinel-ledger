package io.github.vinicius.sentinel.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.vinicius.sentinel.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * OBS-002 / docs/THREAT_MODEL.md's "Secret or personal data leaks into telemetry" row -- captures every log line
 * written during a realistic authenticated request and asserts none of them contain the merchant's own API key
 * secret, the one concrete secret value this integration test context can reliably assert against.
 */
@SpringBootTest(properties = {
	"sentinel.merchant.directory[0].id=11111111-1111-1111-1111-111111111111",
	"sentinel.merchant.directory[0].api-key-id=sentinel-dev-merchant",
	"sentinel.merchant.directory[0].api-key-secret=sentinel-dev-secret-for-redaction-test"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StructuredLoggingRedactionTests {

	private static final String MERCHANT_USERNAME = "sentinel-dev-merchant";
	private static final String MERCHANT_PASSWORD = "sentinel-dev-secret-for-redaction-test";

	@Autowired
	private MockMvc mockMvc;

	private ListAppender<ILoggingEvent> appender;

	@BeforeEach
	void attachAppender() {
		appender = new ListAppender<>();
		appender.start();
		rootLogger().addAppender(appender);
	}

	@AfterEach
	void detachAppender() {
		rootLogger().detachAppender(appender);
	}

	@Test
	void authenticatedRequestsNeverLogTheMerchantApiKeySecret() throws Exception {
		mockMvc.perform(post("/api/v1/payment-intents")
			.with(httpBasic(MERCHANT_USERNAME, MERCHANT_PASSWORD))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"amountInMinorUnits\":\"5000\",\"currency\":\"BRL\"}"));

		List<String> loggedMessages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();

		assertThat(loggedMessages).noneMatch(message -> message.contains(MERCHANT_PASSWORD));
		assertThat(loggedMessages).noneMatch(message -> message.contains("Authorization: Basic"));
	}

	private static Logger rootLogger() {
		return (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
	}
}
