package com.seoulchonnom.slcnapp.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.slcnapp.user.domain.Authority;
import com.seoulchonnom.slcnapp.user.domain.Role;
import com.seoulchonnom.slcnapp.user.domain.User;
import com.seoulchonnom.slcnapp.user.dto.UserLoginRequest;
import com.seoulchonnom.slcnapp.user.dto.UserRegisterRequest;
import com.seoulchonnom.slcnapp.user.exception.InvalidUserException;
import com.seoulchonnom.slcnapp.user.repository.AuthorityRepository;
import com.seoulchonnom.slcnapp.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

	private final UserRepository userRepository;
	private final AuthorityRepository authorityRepository;
	private final PasswordEncoder passwordEncoder;

	public void registerUser(UserRegisterRequest userRegisterRequest) {
		User user = User.builder()
			.name(userRegisterRequest.getName())
			.username(userRegisterRequest.getUserName())
			.password(passwordEncoder.encode(userRegisterRequest.getPassword()))
			.build();

		userRepository.save(user);

		Authority authority = Authority.builder().role(Role.USER).user(user).build();
		authorityRepository.save(authority);
	}

	@Transactional(readOnly = true)
	public boolean loginUser(UserLoginRequest userLoginRequest) {
		User user = userRepository.findByUsername(userLoginRequest.getUsername())
			.orElseThrow(InvalidUserException::new);
		return passwordEncoder.matches(userLoginRequest.getPassword(), user.getPassword());
	}

}
