package io.github.vinicius.sentinel.merchant.internal;

import io.github.vinicius.sentinel.merchant.CurrentMerchantResolver;
import io.github.vinicius.sentinel.merchant.MerchantId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
final class SecurityContextCurrentMerchantResolver implements CurrentMerchantResolver {

	@Override
	public MerchantId requireCurrentMerchantId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null
			|| !authentication.isAuthenticated()
			|| !(authentication.getPrincipal() instanceof MerchantPrincipal principal)) {
			throw new IllegalStateException("no authenticated merchant is available");
		}
		return principal.merchantId();
	}
}
