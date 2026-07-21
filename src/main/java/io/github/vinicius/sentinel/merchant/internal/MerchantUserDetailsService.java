package io.github.vinicius.sentinel.merchant.internal;

import io.github.vinicius.sentinel.merchant.MerchantId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@EnableConfigurationProperties(MerchantDirectoryProperties.class)
final class MerchantUserDetailsService implements UserDetailsService {

	private final Map<String, MerchantPrincipal> principalsByUsername;

	MerchantUserDetailsService(MerchantDirectoryProperties properties, PasswordEncoder passwordEncoder) {
		this.principalsByUsername = properties.directory().stream()
			.collect(Collectors.toUnmodifiableMap(
				MerchantDirectoryProperties.MerchantCredential::apiKeyId,
				credential -> new MerchantPrincipal(
					new MerchantId(credential.id()),
					credential.apiKeyId(),
					passwordEncoder.encode(credential.apiKeySecret())
				)
			));
	}

	@Override
	public UserDetails loadUserByUsername(String username) {
		MerchantPrincipal principal = principalsByUsername.get(username);
		if (principal == null) {
			throw new UsernameNotFoundException("no merchant configured for the supplied credential");
		}
		return principal;
	}
}
