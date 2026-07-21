package io.github.vinicius.sentinel.payments.internal.web;

final class InvalidIdempotencyKeyException extends RuntimeException {

	InvalidIdempotencyKeyException() {
		super("the Idempotency-Key header must be 16-128 visible ASCII characters");
	}
}
