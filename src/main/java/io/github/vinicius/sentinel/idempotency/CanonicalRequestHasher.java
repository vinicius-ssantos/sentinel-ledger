package io.github.vinicius.sentinel.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Hashes {@code operationName + "\n" + merchantId + "\n" + canonicalCommandJson} with SHA-256, as required by
 * docs/IDEMPOTENCY_AND_ERRORS.md. Canonicalization sorts object properties and map entries alphabetically so
 * equivalent JSON member ordering always produces the same hash; it does not implement full RFC 8785 (JCS)
 * number/Unicode formatting, which the MVP's string/UUID/integer-only canonical commands never exercise.
 */
public final class CanonicalRequestHasher {

	private static final ObjectMapper CANONICAL_MAPPER = JsonMapper.builder()
		.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
		.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
		.build();

	private CanonicalRequestHasher() {
	}

	public static String hash(String operationName, UUID merchantId, Object canonicalCommand) {
		String payload = operationName + "\n" + merchantId + "\n" + CANONICAL_MAPPER.writeValueAsString(canonicalCommand);
		return sha256Hex(payload);
	}

	private static String sha256Hex(String payload) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}
}
