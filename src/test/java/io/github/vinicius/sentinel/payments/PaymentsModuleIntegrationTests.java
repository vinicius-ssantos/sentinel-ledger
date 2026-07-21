package io.github.vinicius.sentinel.payments;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;

@ApplicationModuleTest(ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@Import({TestcontainersConfiguration.class, PaymentsModuleIntegrationTests.StubPspConfiguration.class})
class PaymentsModuleIntegrationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void bootstrapsPaymentsModuleInIsolation() {
		assertThat(applicationContext).isNotNull();
	}

	/**
	 * {@code integration.psp} implements {@code payments}' own PspAuthorizationPort, so it is never one of
	 * payments' allowed dependencies and DIRECT_DEPENDENCIES bootstrap cannot see it. A stub stands in for it here,
	 * the way Spring Modulith expects an isolated module test to handle a port implemented by another module.
	 */
	@TestConfiguration
	static class StubPspConfiguration {

		@Bean
		PspAuthorizationPort pspAuthorizationPort() {
			return new PspAuthorizationPort() {
				@Override
				public PspAuthorizationResult authorize(PspAuthorizationRequest request) {
					return new PspAuthorizationResult.Approved(new PspProviderReference("stub"));
				}

				@Override
				public PspAuthorizationResult checkStatus(PspAttemptId attemptId) {
					return new PspAuthorizationResult.Unknown();
				}
			};
		}
	}
}
