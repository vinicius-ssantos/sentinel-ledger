package io.github.vinicius.sentinel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SentinelLedgerApplicationTests {

	@Autowired
	private HealthEndpoint healthEndpoint;

	@Test
	void contextLoads() {
	}

	@Test
	void healthReportsUp() {
		assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);
	}
}
