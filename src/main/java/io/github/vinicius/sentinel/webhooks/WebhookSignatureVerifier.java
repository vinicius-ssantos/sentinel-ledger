package io.github.vinicius.sentinel.webhooks;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * The verification half of the signing scheme in {@code webhooks.internal.WebhookSigner}, and the reference a
 * receiver implementation is expected to follow. It deliberately covers only signature and timestamp validity --
 * replay rejection needs a durable seen-delivery-id store that only the receiver can own (the same "inbox" pattern
 * {@code integration.messaging} uses for its own consumer), so composing this method with such a store is left to
 * the caller. See {@code WebhookSignatureVerifierTests} for a complete example including that composition.
 *
 * <p>Header format: {@code t=<unix seconds>,v1=<hex HMAC-SHA256>}, where the signed content is
 * {@code <timestamp>.<deliveryId>.<rawBody>}. Multiple candidate secrets may be supplied to support a rotation
 * window in which both the current and previous secret are accepted.
 */
public final class WebhookSignatureVerifier {

	private WebhookSignatureVerifier() {
	}

	public record Result(boolean valid, String reason) {

		public static Result ok() {
			return new Result(true, null);
		}

		public static Result rejected(String reason) {
			return new Result(false, reason);
		}
	}

	public static Result verify(
		String signatureHeader, String deliveryId, String rawBody, List<String> candidateSecrets, Instant now, Duration tolerance
	) {
		Objects.requireNonNull(deliveryId, "deliveryId must not be null");
		Objects.requireNonNull(rawBody, "rawBody must not be null");
		Objects.requireNonNull(candidateSecrets, "candidateSecrets must not be null");
		Objects.requireNonNull(now, "now must not be null");
		Objects.requireNonNull(tolerance, "tolerance must not be null");

		if (signatureHeader == null || signatureHeader.isBlank()) {
			return Result.rejected("missing signature header");
		}

		String timestampPart = null;
		String signaturePart = null;
		for (String element : signatureHeader.split(",")) {
			String[] keyValue = element.split("=", 2);
			if (keyValue.length != 2) {
				continue;
			}
			if (keyValue[0].equals("t")) {
				timestampPart = keyValue[1];
			} else if (keyValue[0].equals("v1")) {
				signaturePart = keyValue[1];
			}
		}
		if (timestampPart == null || signaturePart == null) {
			return Result.rejected("malformed signature header");
		}

		long epochSeconds;
		try {
			epochSeconds = Long.parseLong(timestampPart);
		} catch (NumberFormatException e) {
			return Result.rejected("malformed timestamp");
		}
		Instant signedAt = Instant.ofEpochSecond(epochSeconds);
		if (signedAt.isBefore(now.minus(tolerance)) || signedAt.isAfter(now.plus(tolerance))) {
			return Result.rejected("timestamp outside tolerance window");
		}

		String signedContent = epochSeconds + "." + deliveryId + "." + rawBody;
		for (String secret : candidateSecrets) {
			String expected = hmacSha256Hex(secret, signedContent);
			if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signaturePart.getBytes(StandardCharsets.UTF_8))) {
				return Result.ok();
			}
		}
		return Result.rejected("signature mismatch");
	}

	static String hmacSha256Hex(String secret, String content) {
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
