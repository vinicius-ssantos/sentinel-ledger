package io.github.vinicius.sentinel.merchant;

public interface CurrentMerchantResolver {

	/**
	 * Resolves the merchant identity of the authenticated principal.
	 *
	 * @throws IllegalStateException when no authenticated merchant principal is available
	 */
	MerchantId requireCurrentMerchantId();
}
