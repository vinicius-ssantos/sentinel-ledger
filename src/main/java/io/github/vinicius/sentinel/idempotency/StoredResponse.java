package io.github.vinicius.sentinel.idempotency;

public record StoredResponse(int status, String contentType, String body, String location) {
}
