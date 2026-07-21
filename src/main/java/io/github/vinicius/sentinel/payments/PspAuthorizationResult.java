package io.github.vinicius.sentinel.payments;

/**
 * Provider-neutral outcome of an authorization attempt or status lookup. {@code Unknown} represents transport
 * uncertainty explicitly: it covers both a timeout before the provider processed the request and a timeout after
 * processing whose response was lost, because the caller cannot and must not distinguish the two without
 * additional evidence from {@link PspAuthorizationPort#checkStatus(PspAttemptId)}.
 */
public sealed interface PspAuthorizationResult {

	record Approved(PspProviderReference reference) implements PspAuthorizationResult {}

	record Declined(String reasonCode) implements PspAuthorizationResult {}

	record Unknown() implements PspAuthorizationResult {}

	record RetryableFailure(String detail) implements PspAuthorizationResult {}

	record PermanentFailure(String detail) implements PspAuthorizationResult {}
}
