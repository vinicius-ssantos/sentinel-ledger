package io.github.vinicius.sentinel.merchant;

import java.util.Objects;
import java.util.UUID;

public record MerchantId(UUID value) {
	public MerchantId {
		Objects.requireNonNull(value, "merchant id must not be null");
	}
}
