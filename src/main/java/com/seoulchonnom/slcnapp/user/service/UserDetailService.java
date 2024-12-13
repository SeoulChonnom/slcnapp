package com.seoulchonnom.slcnapp.user.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.slcnapp.user.domain.User;
import com.seoulchonnom.slcnapp.user.dto.UserDetail;
import com.seoulchonnom.slcnapp.user.exception.InvalidUserException;
import com.seoulchonnom.slcnapp.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDetailService implements UserDetailsService {

	private final UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		User user = userRepository.findByUsername(username)
			.orElseThrow(InvalidUserException::new);
		return new UserDetail(user);
	}
}
