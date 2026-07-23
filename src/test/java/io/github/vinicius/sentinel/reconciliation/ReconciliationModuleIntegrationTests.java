package io.github.vinicius.sentinel.reconciliation;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.idempotency.IdempotencyAcquisition;
import io.github.vinicius.sentinel.idempotency.IdempotencyGateway;
import io.github.vinicius.sentinel.idempotency.IdempotencyKey;
import io.github.vinicius.sentinel.idempotency.StoredResponse;
import io.github.vinicius.sentinel.merchant.CurrentMerchantResolver;
import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.outbox.OutboxEvent;
import io.github.vinicius.sentinel.outbox.OutboxGateway;
import io.github.vinicius.sentinel.payments.PspAttemptId;
import io.github.vinicius.sentinel.payments.PspAuthorizationPort;
import io.github.vinicius.sentinel.payments.PspAuthorizationRequest;
import io.github.vinicius.sentinel.payments.PspAuthorizationResult;
import io.github.vinicius.sentinel.payments.PspProviderReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DIRECT_DEPENDENCIES only loads reconciliation's own declared list (payments, ledger, integration.psp, audit,
 * money) -- not payments' further dependencies (idempotency, merchant, outbox). Stubs stand in for those, the same
 * way PaymentsModuleIntegrationTests stubs the PSP port that is dependency-inverted from payments' own perspective.
 */
@ApplicationModuleTest(ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@Import({TestcontainersConfiguration.class, ReconciliationModuleIntegrationTests.StubConfiguration.class})
class ReconciliationModuleIntegrationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void bootstrapsReconciliationModuleInIsolation() {
		assertThat(applicationContext).isNotNull();
	}

	@TestConfiguration
	static class StubConfiguration {

		@Bean
		PasswordEncoder passwordEncoder() {
			return new BCryptPasswordEncoder();
		}

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

		@Bean
		CurrentMerchantResolver currentMerchantResolver() {
			return () -> new MerchantId(UUID.randomUUID());
		}

		@Bean
		OutboxGateway outboxGateway() {
			return (OutboxEvent event) -> { };
		}

		@Bean
		IdempotencyGateway idempotencyGateway() {
			return new IdempotencyGateway() {
				@Override
				public IdempotencyAcquisition acquire(UUID merchantId, String operation, IdempotencyKey key, String requestHash) {
					return new IdempotencyAcquisition.Acquired();
				}

				@Override
				public void complete(UUID merchantId, String operation, IdempotencyKey key, StoredResponse response) {
				}

				@Override
				public void failTerminal(UUID merchantId, String operation, IdempotencyKey key, StoredResponse response) {
				}

				@Override
				public void markRecoveryRequired(UUID merchantId, String operation, IdempotencyKey key, String resourceId) {
				}
			};
		}
	}
}
