package io.github.vinicius.sentinel.reconciliation.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class OperatorSecurityBeansConfiguration {

	@Bean
	AuthenticationProvider operatorAuthenticationProvider(OperatorUserDetailsService operatorUserDetailsService, PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(operatorUserDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return provider;
	}
}
