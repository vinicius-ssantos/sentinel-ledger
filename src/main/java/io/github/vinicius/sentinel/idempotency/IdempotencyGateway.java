package io.github.vinicius.sentinel.idempotency;

import java.util.UUID;

public interface IdempotencyGateway {

	IdempotencyAcquisition acquire(UUID merchantId, String operationName, IdempotencyKey key, String requestHash);

	/**
	 * Marks a durable resource as created for this request before an external call whose outcome is not yet known.
	 * A restart or retry that observes this state must recover from the resource and provider evidence instead of
	 * repeating the call blindly.
	 */
	void markRecoveryRequired(UUID merchantId, String operationName, IdempotencyKey key, String resourceId);

	void complete(UUID merchantId, String operationName, IdempotencyKey key, StoredResponse response);

	void failTerminal(UUID merchantId, String operationName, IdempotencyKey key, StoredResponse response);
}
