package io.github.vinicius.sentinel.integration.psp;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class IntegrationPspModuleIntegrationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void bootstrapsIntegrationPspModuleInIsolation() {
		assertThat(applicationContext).isNotNull();
	}
}
