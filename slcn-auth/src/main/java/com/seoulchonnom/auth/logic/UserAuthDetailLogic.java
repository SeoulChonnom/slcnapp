package com.seoulchonnom.auth.logic;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.seoulchonnom.auth.store.UserAuthStore;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAuthDetailLogic implements UserDetailsService {
	private final UserAuthStore userAuthStore;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return userAuthStore.getUserDetail(username);
	}
}
