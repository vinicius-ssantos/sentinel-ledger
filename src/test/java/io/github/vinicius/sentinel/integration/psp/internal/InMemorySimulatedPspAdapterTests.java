package io.github.vinicius.sentinel.integration.psp.internal;

import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PspAttemptId;
import io.github.vinicius.sentinel.payments.PspAuthorizationRequest;
import io.github.vinicius.sentinel.payments.PspAuthorizationResult;
import io.github.vinicius.sentinel.payments.PspCallback;
import io.github.vinicius.sentinel.payments.PspProviderReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemorySimulatedPspAdapterTests {

	private final InMemorySimulatedPspAdapter adapter = new InMemorySimulatedPspAdapter();

	@BeforeEach
	void resetBetweenTests() {
		adapter.reset();
	}

	@Test
	void anUnprogrammedAttemptAutomaticallyApproves() {
		PspAuthorizationRequest request = request();

		PspAuthorizationResult result = adapter.authorize(request);

		assertThat(result).isInstanceOf(PspAuthorizationResult.Approved.class);
		assertThat(adapter.checkStatus(request.attemptId())).isEqualTo(result);
	}

	@Test
	void programmedApprovalIsReturnedByAuthorizeAndCheckStatus() {
		PspAttemptId attemptId = PspAttemptId.generate();
		PspProviderReference reference = new PspProviderReference("provider-ref-1");
		adapter.programApproval(attemptId, reference);

		PspAuthorizationResult authorizeResult = adapter.authorize(request(attemptId));

		assertThat(authorizeResult).isEqualTo(new PspAuthorizationResult.Approved(reference));
		assertThat(adapter.checkStatus(attemptId)).isEqualTo(authorizeResult);
	}

	@Test
	void programmedDeclineIsReturnedByAuthorizeAndCheckStatus() {
		PspAttemptId attemptId = PspAttemptId.generate();
		adapter.programDecline(attemptId, "INSUFFICIENT_FUNDS");

		PspAuthorizationResult authorizeResult = adapter.authorize(request(attemptId));

		assertThat(authorizeResult).isEqualTo(new PspAuthorizationResult.Declined("INSUFFICIENT_FUNDS"));
		assertThat(adapter.checkStatus(attemptId)).isEqualTo(authorizeResult);
	}

	@Test
	void timeoutBeforeProcessingNeverImpliesDeclineAndStaysUnknown() {
		PspAttemptId attemptId = PspAttemptId.generate();
		adapter.programTimeoutBeforeProcessing(attemptId);

		PspAuthorizationResult authorizeResult = adapter.authorize(request(attemptId));

		assertThat(authorizeResult).isInstanceOf(PspAuthorizationResult.Unknown.class);
		assertThat(adapter.checkStatus(attemptId)).isInstanceOf(PspAuthorizationResult.Unknown.class);
	}

	@Test
	void timeoutAfterProcessingIsRecoverableThroughStatusLookupWithoutARepeatedAuthorization() {
		PspAttemptId attemptId = PspAttemptId.generate();
		PspAuthorizationResult trueOutcome = new PspAuthorizationResult.Approved(new PspProviderReference("provider-ref-2"));
		adapter.programTimeoutAfterProcessing(attemptId, trueOutcome);

		PspAuthorizationResult authorizeResult = adapter.authorize(request(attemptId));
		PspAuthorizationResult recovered = adapter.checkStatus(attemptId);

		assertThat(authorizeResult).isInstanceOf(PspAuthorizationResult.Unknown.class);
		assertThat(recovered).isEqualTo(trueOutcome);
	}

	@Test
	void retryableFailureIsDistinctFromPermanentFailure() {
		PspAttemptId retryable = PspAttemptId.generate();
		PspAttemptId permanent = PspAttemptId.generate();
		adapter.programRetryableFailure(retryable, "gateway-timeout");
		adapter.programPermanentFailure(permanent, "malformed-response");

		assertThat(adapter.authorize(request(retryable))).isEqualTo(new PspAuthorizationResult.RetryableFailure("gateway-timeout"));
		assertThat(adapter.authorize(request(permanent))).isEqualTo(new PspAuthorizationResult.PermanentFailure("malformed-response"));
	}

	@Test
	void statusMismatchReportsADifferentOutcomeThanTheTrueProviderStatus() {
		PspAttemptId attemptId = PspAttemptId.generate();
		PspAuthorizationResult reported = new PspAuthorizationResult.Approved(new PspProviderReference("reported-ref"));
		PspAuthorizationResult trueOutcome = new PspAuthorizationResult.Declined("later-reversed");
		adapter.programStatusMismatch(attemptId, reported, trueOutcome);

		assertThat(adapter.authorize(request(attemptId))).isEqualTo(reported);
		assertThat(adapter.checkStatus(attemptId)).isEqualTo(trueOutcome);
	}

	@Test
	void duplicateCallbackDeliveryReturnsTheSameSequenceAndResult() {
		PspAttemptId attemptId = PspAttemptId.generate();
		adapter.programApproval(attemptId, new PspProviderReference("provider-ref-3"));

		PspCallback first = adapter.deliverCallback(attemptId);
		PspCallback duplicate = adapter.deliverCallback(attemptId);

		assertThat(duplicate).isEqualTo(first);
	}

	@Test
	void outOfOrderCallbackDeliveryCanReturnAnOlderSequenceAfterANewerOneWasAlreadyDelivered() {
		PspAttemptId attemptId = PspAttemptId.generate();
		PspAuthorizationResult reported = new PspAuthorizationResult.Unknown();
		PspAuthorizationResult trueOutcome = new PspAuthorizationResult.Approved(new PspProviderReference("provider-ref-4"));
		adapter.programTimeoutAfterProcessing(attemptId, trueOutcome);

		PspCallback latest = adapter.deliverCallback(attemptId);
		PspCallback olderRedelivered = adapter.deliverCallback(attemptId, 1);

		assertThat(latest.sequence()).isEqualTo(2);
		assertThat(latest.result()).isEqualTo(trueOutcome);
		assertThat(olderRedelivered.sequence()).isEqualTo(1);
		assertThat(olderRedelivered.result()).isEqualTo(reported);
	}

	@Test
	void aSequenceBasedListenerCanIgnoreDuplicateAndRegressedCallbacksWithoutReapplyingTheEffect() {
		PspAttemptId attemptId = PspAttemptId.generate();
		PspAuthorizationResult trueOutcome = new PspAuthorizationResult.Approved(new PspProviderReference("provider-ref-5"));
		adapter.programTimeoutAfterProcessing(attemptId, trueOutcome);

		Set<Long> appliedSequences = new HashSet<>();
		int[] applyCount = {0};

		applyIfNew(adapter.deliverCallback(attemptId, 2), appliedSequences, applyCount);
		applyIfNew(adapter.deliverCallback(attemptId, 2), appliedSequences, applyCount);
		applyIfNew(adapter.deliverCallback(attemptId, 1), appliedSequences, applyCount);

		assertThat(applyCount[0]).isEqualTo(1);
	}

	private static void applyIfNew(PspCallback callback, Set<Long> appliedSequences, int[] applyCount) {
		if (appliedSequences.add(callback.sequence()) && callback.sequence() == 2) {
			applyCount[0]++;
		}
	}

	@Test
	void requestingACallbackForAnUnknownAttemptFails() {
		assertThatThrownBy(() -> adapter.deliverCallback(PspAttemptId.generate()))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void resetClearsProgrammedAttempts() {
		PspAttemptId attemptId = PspAttemptId.generate();
		adapter.programDecline(attemptId, "reason");

		adapter.reset();

		assertThat(adapter.checkStatus(attemptId)).isInstanceOf(PspAuthorizationResult.Unknown.class);
	}

	private static PspAuthorizationRequest request() {
		return request(PspAttemptId.generate());
	}

	private static PspAuthorizationRequest request(PspAttemptId attemptId) {
		return new PspAuthorizationRequest(
			attemptId,
			PaymentIntentId.generate(),
			new MerchantId(UUID.randomUUID()),
			Money.positive(1_000, Currency.BRL)
		);
	}
}
