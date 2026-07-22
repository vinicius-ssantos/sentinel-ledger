package io.github.vinicius.sentinel.audit;

public interface AuditGateway {

	/**
	 * Persists the event in the caller's current local transaction. There is no deduplication key: callers are
	 * responsible for invoking this only from a code path that itself runs at most once per business effect (for
	 * example, downstream of an idempotency gate that skips domain logic entirely on replay).
	 */
	void record(AuditEvent event);
}
