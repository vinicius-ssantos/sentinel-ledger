package io.github.vinicius.sentinel.payments;

/**
 * Thrown by {@link PaymentIntentStore#save(PaymentIntent)} when the stored row was modified since the aggregate was
 * loaded, so the caller lost the optimistic-concurrency race and must reload before retrying.
 */
public final class OptimisticLockException extends RuntimeException {

	public OptimisticLockException(PaymentIntentId id, long expectedVersion) {
		super("payment intent " + id + " was concurrently modified; expected stored version " + (expectedVersion - 1));
	}
}
