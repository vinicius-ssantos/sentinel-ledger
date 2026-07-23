package io.github.vinicius.sentinel.outbox;

public interface OutboxGateway {

	/**
	 * Persists the publication intent in the caller's current local transaction. There is no deduplication key:
	 * callers are responsible for invoking this only from a code path that itself runs at most once per business
	 * effect (for example, downstream of an idempotency gate that skips domain logic entirely on replay), the same
	 * contract as {@code audit.AuditGateway#record}.
	 */
	void enqueue(OutboxEvent event);
}
