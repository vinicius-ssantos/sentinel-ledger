package io.github.vinicius.sentinel.merchant.internal;

import io.github.vinicius.sentinel.merchant.MerchantId;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Objects;

final class MerchantPrincipal implements UserDetails {

	private final MerchantId merchantId;
	private final String username;
	private final String encodedPassword;

	MerchantPrincipal(MerchantId merchantId, String username, String encodedPassword) {
		this.merchantId = Objects.requireNonNull(merchantId, "merchantId must not be null");
		this.username = Objects.requireNonNull(username, "username must not be null");
		this.encodedPassword = Objects.requireNonNull(encodedPassword, "encodedPassword must not be null");
	}

	MerchantId merchantId() {
		return merchantId;
	}

	@Override
	public List<GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_MERCHANT"));
	}

	@Override
	public String getPassword() {
		return encodedPassword;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}
