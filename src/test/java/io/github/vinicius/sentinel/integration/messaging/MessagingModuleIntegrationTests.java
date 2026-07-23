package io.github.vinicius.sentinel.integration.messaging;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots with messaging disabled (the suite default), proving no module needs a broker just to start.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class MessagingModuleIntegrationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void bootstrapsMessagingModuleInIsolationWithoutABroker() {
		assertThat(applicationContext).isNotNull();
	}
}
