package io.github.vinicius.sentinel.webhooks;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WHK-001: this is the reference a receiver implementation follows. Signature and timestamp validity are checked
 * by {@link WebhookSignatureVerifier#verify}; replay rejection is demonstrated here by composing that result with
 * a seen-delivery-id store the receiver owns, exactly as the class's own documentation describes.
 */
class WebhookSignatureVerifierTests {

	private static final String SECRET = "test-secret";
	private static final String DELIVERY_ID = "11111111-1111-1111-1111-111111111111";
	private static final String BODY = "{\"eventType\":\"payment.captured\"}";
	private static final Duration TOLERANCE = Duration.ofMinutes(5);

	@Test
	void acceptsACorrectlySignedRequest() {
		Instant now = Instant.now();
		String header = sign(now, DELIVERY_ID, BODY, SECRET);

		WebhookSignatureVerifier.Result result = WebhookSignatureVerifier.verify(
			header, DELIVERY_ID, BODY, List.of(SECRET), now, TOLERANCE
		);

		assertThat(result.valid()).isTrue();
	}

	@Test
	void rejectsAnInvalidSignature() {
		Instant now = Instant.now();
		String header = sign(now, DELIVERY_ID, BODY, "wrong-secret");

		WebhookSignatureVerifier.Result result = WebhookSignatureVerifier.verify(
			header, DELIVERY_ID, BODY, List.of(SECRET), now, TOLERANCE
		);

		assertThat(result.valid()).isFalse();
		assertThat(result.reason()).isEqualTo("signature mismatch");
	}

	@Test
	void rejectsATamperedBody() {
		Instant now = Instant.now();
		String header = sign(now, DELIVERY_ID, BODY, SECRET);

		WebhookSignatureVerifier.Result result = WebhookSignatureVerifier.verify(
			header, DELIVERY_ID, BODY + "tampered", List.of(SECRET), now, TOLERANCE
		);

		assertThat(result.valid()).isFalse();
	}

	@Test
	void rejectsAnExpiredTimestamp() {
		Instant now = Instant.now();
		Instant signedAt = now.minus(Duration.ofMinutes(10));
		String header = sign(signedAt, DELIVERY_ID, BODY, SECRET);

		WebhookSignatureVerifier.Result result = WebhookSignatureVerifier.verify(
			header, DELIVERY_ID, BODY, List.of(SECRET), now, TOLERANCE
		);

		assertThat(result.valid()).isFalse();
		assertThat(result.reason()).isEqualTo("timestamp outside tolerance window");
	}

	@Test
	void rejectsATimestampTooFarInTheFuture() {
		Instant now = Instant.now();
		Instant signedAt = now.plus(Duration.ofMinutes(10));
		String header = sign(signedAt, DELIVERY_ID, BODY, SECRET);

		WebhookSignatureVerifier.Result result = WebhookSignatureVerifier.verify(
			header, DELIVERY_ID, BODY, List.of(SECRET), now, TOLERANCE
		);

		assertThat(result.valid()).isFalse();
	}

	@Test
	void rejectsAMissingSignatureHeader() {
		WebhookSignatureVerifier.Result result = WebhookSignatureVerifier.verify(
			null, DELIVERY_ID, BODY, List.of(SECRET), Instant.now(), TOLERANCE
		);

		assertThat(result.valid()).isFalse();
		assertThat(result.reason()).isEqualTo("missing signature header");
	}

	@Test
	void rejectsAMalformedSignatureHeader() {
		WebhookSignatureVerifier.Result result = WebhookSignatureVerifier.verify(
			"not-a-valid-header", DELIVERY_ID, BODY, List.of(SECRET), Instant.now(), TOLERANCE
		);

		assertThat(result.valid()).isFalse();
		assertThat(result.reason()).isEqualTo("malformed signature header");
	}

	@Test
	void acceptsThePreviousSecretDuringARotationWindow() {
		Instant now = Instant.now();
		String currentSecret = "new-secret";
		String previousSecret = "old-secret";
		String header = sign(now, DELIVERY_ID, BODY, previousSecret);

		WebhookSignatureVerifier.Result result = WebhookSignatureVerifier.verify(
			header, DELIVERY_ID, BODY, List.of(currentSecret, previousSecret), now, TOLERANCE
		);

		assertThat(result.valid()).isTrue();
	}

	/**
	 * The receiver example: verify, then consult a durable seen-delivery-id store before accepting. A real
	 * receiver persists this set (the same "inbox" pattern {@code integration.messaging} uses for its own
	 * consumer) so it survives a restart; an in-memory set is enough to demonstrate the composition here.
	 */
	@Test
	void receiverExampleRejectsAReplayedDeliveryEvenWithAValidSignature() {
		Instant now = Instant.now();
		String header = sign(now, DELIVERY_ID, BODY, SECRET);
		Set<String> seenDeliveryIds = new HashSet<>();

		boolean firstAccepted = receive(header, DELIVERY_ID, BODY, now, seenDeliveryIds);
		boolean secondAccepted = receive(header, DELIVERY_ID, BODY, now, seenDeliveryIds);

		assertThat(firstAccepted).isTrue();
		assertThat(secondAccepted).isFalse();
	}

	private static boolean receive(String header, String deliveryId, String body, Instant now, Set<String> seenDeliveryIds) {
		WebhookSignatureVerifier.Result result = WebhookSignatureVerifier.verify(header, deliveryId, body, List.of(SECRET), now, TOLERANCE);
		if (!result.valid()) {
			return false;
		}
		return seenDeliveryIds.add(deliveryId);
	}

	private static String sign(Instant timestamp, String deliveryId, String body, String secret) {
		long epochSeconds = timestamp.getEpochSecond();
		String signedContent = epochSeconds + "." + deliveryId + "." + body;
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String hex = HexFormat.of().formatHex(mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8)));
			return "t=" + epochSeconds + ",v1=" + hex;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
