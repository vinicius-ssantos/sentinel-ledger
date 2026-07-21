package io.github.vinicius.sentinel.payments.internal;

import io.github.vinicius.sentinel.idempotency.StoredResponse;

public record ApiResult(int status, String contentType, String body, String location, Integer retryAfterSeconds, boolean replayed) {

	public static ApiResult fromStoredResponse(StoredResponse response, boolean replayed) {
		return new ApiResult(response.status(), response.contentType(), response.body(), response.location(), null, replayed);
	}
}
