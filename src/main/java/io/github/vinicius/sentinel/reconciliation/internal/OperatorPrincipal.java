package io.github.vinicius.sentinel.reconciliation.internal;

import io.github.vinicius.sentinel.reconciliation.OperatorId;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Objects;

final class OperatorPrincipal implements UserDetails {

	private final OperatorId operatorId;
	private final String username;
	private final String encodedPassword;

	OperatorPrincipal(OperatorId operatorId, String username, String encodedPassword) {
		this.operatorId = Objects.requireNonNull(operatorId, "operatorId must not be null");
		this.username = Objects.requireNonNull(username, "username must not be null");
		this.encodedPassword = Objects.requireNonNull(encodedPassword, "encodedPassword must not be null");
	}

	OperatorId operatorId() {
		return operatorId;
	}

	@Override
	public List<GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"));
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
