package io.github.vinicius.sentinel.idempotency;

import java.util.UUID;

public interface IdempotencyGateway {

	IdempotencyAcquisition acquire(UUID merchantId, String operationName, IdempotencyKey key, String requestHash);

	void complete(UUID merchantId, String operationName, IdempotencyKey key, StoredResponse response);

	void failTerminal(UUID merchantId, String operationName, IdempotencyKey key, StoredResponse response);
}
