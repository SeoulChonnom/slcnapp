package com.seoulchonnom.auth.store.projection;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.seoulchonnom.spec.user.entity.Role;

public record ClientPrincipal(String clientId, String clientName) {
	private static final Collection<GrantedAuthority> AUTHORITIES = List.of(
		new SimpleGrantedAuthority(Role.CLIENT.name()));

	public Collection<GrantedAuthority> getAuthorities() {
		return AUTHORITIES;
	}
}
