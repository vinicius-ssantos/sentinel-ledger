package io.github.vinicius.sentinel.payments.internal;

import io.github.vinicius.sentinel.money.Money;
import io.github.vinicius.sentinel.payments.PspAttemptId;

record AuthorizationAttempt(ApiResult terminalResult, PspAttemptId attemptId, Money amount, boolean recovering) {

	static AuthorizationAttempt terminal(ApiResult result) {
		return new AuthorizationAttempt(result, null, null, false);
	}

	static AuthorizationAttempt fresh(PspAttemptId attemptId, Money amount) {
		return new AuthorizationAttempt(null, attemptId, amount, false);
	}

	static AuthorizationAttempt recovering(PspAttemptId attemptId, Money amount) {
		return new AuthorizationAttempt(null, attemptId, amount, true);
	}

	boolean isTerminal() {
		return terminalResult != null;
	}
}
