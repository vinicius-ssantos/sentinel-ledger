package io.github.vinicius.sentinel.payments;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;

@ApplicationModuleTest
class PaymentsModuleIntegrationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void bootstrapsPaymentsModuleInIsolation() {
		assertThat(applicationContext).isNotNull();
	}
}
