package io.github.vinicius.sentinel.webhooks;

/**
 * Thrown by {@link WebhookDispatchPort#deliver} when the merchant endpoint could not be reached or responded with
 * a non-2xx status. The caller (the messaging consumer) is expected to let this propagate so its own bounded
 * retry/dead-letter policy governs the retry, rather than retrying inside this module.
 */
public class WebhookDeliveryFailedException extends RuntimeException {

	public WebhookDeliveryFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	public WebhookDeliveryFailedException(String message) {
		super(message);
	}
}
