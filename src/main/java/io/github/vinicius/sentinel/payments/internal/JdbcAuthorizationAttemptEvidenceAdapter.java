package io.github.vinicius.sentinel.payments.internal;

import io.github.vinicius.sentinel.payments.AuthorizationAttemptEvidencePort;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PspAttemptId;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JdbcAuthorizationAttemptEvidenceAdapter implements AuthorizationAttemptEvidencePort {

	private final AuthorizationAttemptStore attemptStore;

	JdbcAuthorizationAttemptEvidenceAdapter(AuthorizationAttemptStore attemptStore) {
		this.attemptStore = attemptStore;
	}

	@Override
	public Optional<PspAttemptId> lastAttemptId(PaymentIntentId paymentIntentId) {
		return attemptStore.findPendingAttempt(paymentIntentId);
	}
}
