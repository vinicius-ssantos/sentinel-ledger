package io.github.vinicius.sentinel.observability;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CorrelationIdFilterIntegrationTests {

	private static final String HEADER = "X-Correlation-Id";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void generatesACorrelationIdWhenTheCallerDidNotSendOne() throws Exception {
		MvcResult result = mockMvc.perform(get("/actuator/health")).andReturn();

		String correlationId = result.getResponse().getHeader(HEADER);
		assertThat(correlationId).isNotBlank();
	}

	@Test
	void echoesBackTheCallersOwnCorrelationId() throws Exception {
		String suppliedId = "test-correlation-abc123";

		MvcResult result = mockMvc.perform(get("/actuator/health").header(HEADER, suppliedId)).andReturn();

		assertThat(result.getResponse().getHeader(HEADER)).isEqualTo(suppliedId);
	}

	@Test
	void aRejectedUnauthenticatedRequestStillCarriesACorrelationId() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/payment-intents/00000000-0000-0000-0000-000000000000")).andReturn();

		assertThat(result.getResponse().getHeader(HEADER)).isNotBlank();
	}
}
