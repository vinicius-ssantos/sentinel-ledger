package io.github.vinicius.sentinel.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Two independent credential realms share one HTTP Basic mechanism: a merchant principal carries {@code
 * ROLE_MERCHANT}, an operator principal carries {@code ROLE_OPERATOR} (see reconciliation.internal.OperatorPrincipal),
 * and each module contributes its own {@link AuthenticationProvider} bean rather than this class knowing either
 * module's internal user-lookup type. The privileged operator API is restricted to {@code ROLE_OPERATOR}; the rest
 * of {@code /api/**} accepts either authenticated role, with ownership enforced downstream by each resource.
 */
@Configuration
class SecurityConfig {

	@Bean
	AuthenticationManager authenticationManager(List<AuthenticationProvider> providers) {
		return new ProviderManager(providers);
	}

	@Bean
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/actuator/health", "/actuator/info").permitAll()
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.requestMatchers("/api/v1/reconciliation/**").hasRole("OPERATOR")
				.requestMatchers("/api/**").authenticated()
				.anyRequest().denyAll())
			.httpBasic(Customizer.withDefaults());
		return http.build();
	}
}
