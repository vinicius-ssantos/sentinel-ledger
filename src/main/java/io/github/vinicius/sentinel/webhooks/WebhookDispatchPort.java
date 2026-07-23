package io.github.vinicius.sentinel.webhooks;

public interface WebhookDispatchPort {

	/**
	 * Signs and sends {@code request} to the merchant's registered endpoint, recording the attempt against
	 * {@code request.id()} regardless of outcome. Throws {@link WebhookDeliveryFailedException} on any failure
	 * (unreachable endpoint, non-2xx response) instead of retrying internally.
	 */
	void deliver(WebhookDeliveryRequest request);

	/**
	 * Called once the caller's own retry budget for {@code id} is exhausted, marking the delivery history
	 * {@link WebhookDeliveryStatus#FAILED} instead of leaving it looking still-pending forever.
	 */
	void markExhausted(WebhookDeliveryId id, String reason);
}
