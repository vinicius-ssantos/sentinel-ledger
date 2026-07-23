package io.github.vinicius.sentinel.webhooks;

import java.util.Objects;
import java.util.UUID;

/**
 * Equal to the outbox event id it originated from, so a retried delivery (whichever layer retries it) always
 * carries the same identity instead of minting a new one per attempt.
 */
public record WebhookDeliveryId(UUID value) {

	public WebhookDeliveryId {
		Objects.requireNonNull(value, "webhook delivery id must not be null");
	}
}
