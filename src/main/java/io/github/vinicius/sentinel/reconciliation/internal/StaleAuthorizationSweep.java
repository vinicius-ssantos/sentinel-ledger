package io.github.vinicius.sentinel.reconciliation.internal;

import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PaymentIntentStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Recovers payment intents still AUTHORIZATION_PENDING/UNKNOWN after {@code sentinel.reconciliation.stale-after}
 * (default 15 minutes), independent of whether the original client ever retries -- the scheduled half of ADR-005's
 * "recover through provider status lookup, callbacks, or reconciliation." Each candidate goes through the same
 * {@link PaymentIntentReconciliationCheckService#check} an on-demand operator check would use, so this class is a
 * thin trigger, not a second copy of the detection logic. A failing candidate aborts the rest of that cycle; the
 * next scheduled run retries it. Structured failure visibility is deferred to the observability work in #25.
 */
@Component
@EnableConfigurationProperties(ReconciliationSweepProperties.class)
class StaleAuthorizationSweep {

	private final PaymentIntentStore paymentIntentStore;
	private final PaymentIntentReconciliationCheckService checkService;
	private final ReconciliationSweepProperties properties;

	StaleAuthorizationSweep(
		PaymentIntentStore paymentIntentStore,
		PaymentIntentReconciliationCheckService checkService,
		ReconciliationSweepProperties properties
	) {
		this.paymentIntentStore = paymentIntentStore;
		this.checkService = checkService;
		this.properties = properties;
	}

	@Scheduled(fixedDelayString = "${sentinel.reconciliation.sweep-interval:PT5M}")
	void sweep() {
		Instant threshold = Instant.now().minus(properties.staleAfter());
		for (PaymentIntentId paymentIntentId : paymentIntentStore.findAuthorizationPendingOlderThan(threshold)) {
			checkService.check(paymentIntentId);
		}
	}
}
