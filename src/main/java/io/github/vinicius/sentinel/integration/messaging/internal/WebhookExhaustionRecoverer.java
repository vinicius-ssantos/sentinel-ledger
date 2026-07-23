package io.github.vinicius.sentinel.integration.messaging.internal;

import io.github.vinicius.sentinel.webhooks.WebhookDeliveryId;
import io.github.vinicius.sentinel.webhooks.WebhookDispatchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;

import java.util.UUID;

/**
 * Marks the webhook delivery {@code FAILED} before handing the message to {@link RejectAndDontRequeueRecoverer}
 * for dead-lettering, so "final failure" is a queryable, timeline-visible fact instead of only existing as a row
 * sitting in the dead-letter queue.
 */
class WebhookExhaustionRecoverer implements MessageRecoverer {

	private static final Logger log = LoggerFactory.getLogger(WebhookExhaustionRecoverer.class);

	private final WebhookDispatchPort webhookDispatchPort;
	private final MessageRecoverer delegate = new RejectAndDontRequeueRecoverer();

	WebhookExhaustionRecoverer(WebhookDispatchPort webhookDispatchPort) {
		this.webhookDispatchPort = webhookDispatchPort;
	}

	@Override
	public void recover(Message message, Throwable cause) {
		String messageId = message.getMessageProperties().getMessageId();
		if (messageId != null && !messageId.isBlank()) {
			try {
				webhookDispatchPort.markExhausted(new WebhookDeliveryId(UUID.fromString(messageId)), safeMessage(cause));
			} catch (Exception e) {
				log.warn("failed to mark webhook delivery {} exhausted", messageId, e);
			}
		}
		delegate.recover(message, cause);
	}

	private static String safeMessage(Throwable cause) {
		String message = cause.getMessage();
		return message == null ? cause.getClass().getSimpleName() : message;
	}
}
