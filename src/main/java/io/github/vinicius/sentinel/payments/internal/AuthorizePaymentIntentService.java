package io.github.vinicius.sentinel.payments.internal;

import io.github.vinicius.sentinel.idempotency.IdempotencyKey;
import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PspAuthorizationPort;
import io.github.vinicius.sentinel.payments.PspAuthorizationRequest;
import io.github.vinicius.sentinel.payments.PspAuthorizationResult;
import org.springframework.stereotype.Service;

import java.net.URI;

/**
 * Orchestrates the persist-call-persist authorization workflow without holding a database transaction across the
 * PSP call: {@link AuthorizePaymentIntentSteps#beginAuthorization} commits and returns before the PSP is called
 * here, and {@link AuthorizePaymentIntentSteps#resolveAuthorization} opens a fresh transaction afterward.
 */
@Service
public class AuthorizePaymentIntentService {

	private final AuthorizePaymentIntentSteps steps;
	private final PspAuthorizationPort pspAuthorizationPort;

	AuthorizePaymentIntentService(AuthorizePaymentIntentSteps steps, PspAuthorizationPort pspAuthorizationPort) {
		this.steps = steps;
		this.pspAuthorizationPort = pspAuthorizationPort;
	}

	public ApiResult authorize(MerchantId merchantId, IdempotencyKey idempotencyKey, PaymentIntentId paymentIntentId, URI instance) {
		AuthorizationAttempt attempt = steps.beginAuthorization(merchantId, idempotencyKey, paymentIntentId, instance);
		if (attempt.isTerminal()) {
			return attempt.terminalResult();
		}

		PspAuthorizationResult result = attempt.recovering()
			? pspAuthorizationPort.checkStatus(attempt.attemptId())
			: pspAuthorizationPort.authorize(new PspAuthorizationRequest(attempt.attemptId(), paymentIntentId, merchantId, attempt.amount()));

		return steps.resolveAuthorization(merchantId, idempotencyKey, paymentIntentId, attempt.attemptId(), result, instance);
	}
}
