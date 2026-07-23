package io.github.vinicius.sentinel.outbox;

/**
 * Delivers one claimed event to an external system. Implementations are called outside any database transaction,
 * the same discipline as the PSP port: a slow or failing publish must never hold a lock. Delivery is at-least-once
 * — a publish that succeeds externally but whose completion write is lost (worker crash, timeout) is redelivered,
 * so implementations and their consumers must tolerate duplicates. Until a broker adapter exists, the default
 * implementation only records that publication would occur.
 */
public interface OutboxPublisherPort {

	void publish(OutboxRecord event);
}
