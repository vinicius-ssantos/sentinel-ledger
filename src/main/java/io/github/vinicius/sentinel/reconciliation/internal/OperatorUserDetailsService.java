package io.github.vinicius.sentinel.reconciliation.internal;

import io.github.vinicius.sentinel.reconciliation.OperatorId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@EnableConfigurationProperties(OperatorDirectoryProperties.class)
final class OperatorUserDetailsService implements UserDetailsService {

	private final Map<String, OperatorPrincipal> principalsByUsername;

	OperatorUserDetailsService(OperatorDirectoryProperties properties, PasswordEncoder passwordEncoder) {
		this.principalsByUsername = properties.directory().stream()
			.collect(Collectors.toUnmodifiableMap(
				OperatorDirectoryProperties.OperatorCredential::apiKeyId,
				credential -> new OperatorPrincipal(
					new OperatorId(credential.id()),
					credential.apiKeyId(),
					passwordEncoder.encode(credential.apiKeySecret())
				)
			));
	}

	@Override
	public UserDetails loadUserByUsername(String username) {
		OperatorPrincipal principal = principalsByUsername.get(username);
		if (principal == null) {
			throw new UsernameNotFoundException("no operator configured for the supplied credential");
		}
		return principal;
	}
}
