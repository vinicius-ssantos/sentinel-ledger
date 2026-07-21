package io.github.vinicius.sentinel.payments.internal.web;

final class MissingIdempotencyKeyException extends RuntimeException {

	MissingIdempotencyKeyException() {
		super("this mutating command requires an Idempotency-Key header");
	}
}
