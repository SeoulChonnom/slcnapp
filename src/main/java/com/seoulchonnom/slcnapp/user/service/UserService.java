package com.seoulchonnom.slcnapp.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.slcnapp.user.JwtTokenProvider;
import com.seoulchonnom.slcnapp.user.domain.Authority;
import com.seoulchonnom.slcnapp.user.domain.Role;
import com.seoulchonnom.slcnapp.user.domain.User;
import com.seoulchonnom.slcnapp.user.dto.Token;
import com.seoulchonnom.slcnapp.user.dto.UserDetail;
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
	private final JwtTokenProvider jwtTokenProvider;

	public void registerUser(UserRegisterRequest userRegisterRequest) {
		User user = userRegisterRequest.from(passwordEncoder.encode(userRegisterRequest.getPassword()));
		userRepository.save(user);

		Authority authority = Authority.builder().role(Role.USER).user(user).build();
		authorityRepository.save(authority);
	}

	@Transactional(readOnly = true)
	public Token loginUser(UserLoginRequest userLoginRequest) {
		User user = userRepository.findByUsername(userLoginRequest.getUsername())
			.orElseThrow(InvalidUserException::new);

		if (passwordEncoder.matches(userLoginRequest.getPassword(), user.getPassword())) {
			return jwtTokenProvider.createToken(new UserDetail(user));
		} else {
			throw new InvalidUserException();
		}
	}

}
