package com.seoulchonnom.slcnapp.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.slcnapp.user.domain.Role;
import com.seoulchonnom.slcnapp.user.domain.User;
import com.seoulchonnom.slcnapp.user.dto.UserLoginRequest;
import com.seoulchonnom.slcnapp.user.dto.UserRegisterRequest;
import com.seoulchonnom.slcnapp.user.exception.UserNameNotFoundException;
import com.seoulchonnom.slcnapp.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public void registerUser(UserRegisterRequest userRegisterRequest) {
		User user = User.builder()
			.name(userRegisterRequest.getName())
			.username(userRegisterRequest.getUserName())
			.password(passwordEncoder.encode(userRegisterRequest.getPassword()))
			.role(Role.USER)
			.build();

		userRepository.save(user);
	}

	public boolean loginUser(UserLoginRequest userLoginRequest) {
		User user = userRepository.findByUsername(userLoginRequest.getUsername())
			.orElseThrow(UserNameNotFoundException::new);
		return passwordEncoder.matches(userLoginRequest.getPassword(), user.getPassword());
	}

}
