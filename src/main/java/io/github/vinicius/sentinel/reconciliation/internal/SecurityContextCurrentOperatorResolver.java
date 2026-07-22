package io.github.vinicius.sentinel.reconciliation.internal;

import io.github.vinicius.sentinel.reconciliation.CurrentOperatorResolver;
import io.github.vinicius.sentinel.reconciliation.OperatorId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
final class SecurityContextCurrentOperatorResolver implements CurrentOperatorResolver {

	@Override
	public OperatorId requireCurrentOperatorId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null
			|| !authentication.isAuthenticated()
			|| !(authentication.getPrincipal() instanceof OperatorPrincipal principal)) {
			throw new IllegalStateException("no authenticated operator is available");
		}
		return principal.operatorId();
	}
}
