package io.github.vinicius.sentinel.ledger;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class LedgerModuleIntegrationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void bootstrapsLedgerModuleInIsolation() {
		assertThat(applicationContext).isNotNull();
	}
}
