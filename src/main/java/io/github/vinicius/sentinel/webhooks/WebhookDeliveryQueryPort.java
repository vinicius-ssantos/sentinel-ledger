package io.github.vinicius.sentinel.webhooks;

import java.util.List;

public interface WebhookDeliveryQueryPort {

	/**
	 * Every delivery recorded for one aggregate, ordered by {@code createdAt} ascending -- lets a consuming
	 * module (such as the payment timeline) correlate webhook history back to its own resource.
	 */
	List<WebhookDeliveryRecord> findByAggregate(String aggregateType, String aggregateId);

	/**
	 * {@code true} once {@code id} has been successfully delivered. A caller redelivered the same message after
	 * that must not call {@link WebhookDispatchPort#deliver} again -- doing so would re-notify the merchant for an
	 * effect that already applied.
	 */
	boolean isDelivered(WebhookDeliveryId id);
}
