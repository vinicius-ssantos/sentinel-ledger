package io.github.vinicius.sentinel.observability.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Every request carries a correlation id -- the caller's own {@value #HEADER} header if present, otherwise a
 * freshly generated one -- echoed back on the response and placed in the logging MDC for the lifetime of the
 * request, so every structured log line written while handling it (including from a security rejection) carries
 * it. Business code that wants the same id later (a scheduled sweep, a queue consumer) reads it from the audit or
 * outbox evidence already correlated to the resource, not from this filter, which only covers the HTTP boundary.
 * Ordered ahead of Spring Security so even an unauthenticated 401/403 response is correlatable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter extends OncePerRequestFilter {

	static final String HEADER = "X-Correlation-Id";
	static final String MDC_KEY = "correlationId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		String correlationId = request.getHeader(HEADER);
		if (correlationId == null || correlationId.isBlank()) {
			correlationId = UUID.randomUUID().toString();
		}
		response.setHeader(HEADER, correlationId);
		MDC.put(MDC_KEY, correlationId);
		try {
			chain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}
}
