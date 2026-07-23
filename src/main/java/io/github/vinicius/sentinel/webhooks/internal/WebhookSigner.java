package io.github.vinicius.sentinel.webhooks.internal;

import io.github.vinicius.sentinel.webhooks.WebhookDeliveryId;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * The signing half of the scheme {@code WebhookSignatureVerifier} checks. Duplicates that class's small HMAC
 * helper rather than sharing it, the same call made for ReconciledAuthorizationResolver's PSP-result mapping: a
 * few lines of crypto primitive don't justify a cross-module dependency or a shared internal utility.
 */
final class WebhookSigner {

	private WebhookSigner() {
	}

	static String header(Instant timestamp, WebhookDeliveryId id, String rawBody, String secret) {
		long epochSeconds = timestamp.getEpochSecond();
		String signedContent = epochSeconds + "." + id.value() + "." + rawBody;
		return "t=" + epochSeconds + ",v1=" + hmacSha256Hex(secret, signedContent);
	}

	private static String hmacSha256Hex(String secret, String content) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] raw = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(raw);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("HmacSHA256 must be available on this JVM", e);
		}
	}
}
