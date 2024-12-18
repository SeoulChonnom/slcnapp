package com.seoulchonnom.slcnapp.user.service;

import static com.seoulchonnom.slcnapp.user.UserConstant.*;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.slcnapp.user.JwtTokenProvider;
import com.seoulchonnom.slcnapp.user.domain.Authority;
import com.seoulchonnom.slcnapp.user.domain.RefreshToken;
import com.seoulchonnom.slcnapp.user.domain.Role;
import com.seoulchonnom.slcnapp.user.domain.User;
import com.seoulchonnom.slcnapp.user.dto.Token;
import com.seoulchonnom.slcnapp.user.dto.UserDetail;
import com.seoulchonnom.slcnapp.user.dto.UserInfoResponse;
import com.seoulchonnom.slcnapp.user.dto.UserLoginRequest;
import com.seoulchonnom.slcnapp.user.dto.UserRegisterRequest;
import com.seoulchonnom.slcnapp.user.exception.InvalidAccessTokenException;
import com.seoulchonnom.slcnapp.user.exception.InvalidRefreshTokenException;
import com.seoulchonnom.slcnapp.user.exception.InvalidUserException;
import com.seoulchonnom.slcnapp.user.repository.AuthorityRepository;
import com.seoulchonnom.slcnapp.user.repository.RefreshTokenRepository;
import com.seoulchonnom.slcnapp.user.repository.UserRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

	private final UserRepository userRepository;
	private final AuthorityRepository authorityRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;

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

	public Token issueToken(UserLoginRequest userLoginRequest) {
		User user = userRepository.findByUsername(userLoginRequest.getUsername())
			.orElseThrow(InvalidUserException::new);

		if (passwordEncoder.matches(userLoginRequest.getPassword(), user.getPassword())) {

			Token token = jwtTokenProvider.createToken(new UserDetail(user));
			RefreshToken refreshToken = refreshTokenRepository.findByUserId(user.getId())
				.orElseGet(() -> RefreshToken.builder().token(token.getRefreshToken()).userId(user.getId()).build());

			refreshTokenRepository.save(refreshToken);

			return token;
		} else {
			throw new InvalidUserException();
		}
	}

	public void updateCookie(HttpServletResponse response, String token) {
		Cookie cookie = new Cookie("refreshToken", token);

		cookie.setSecure(true);
		cookie.setHttpOnly(true);
		cookie.setPath("/");
		cookie.setMaxAge(COOKIE_EXPIRE_TIME);

		response.addCookie(cookie);
	}

	@Transactional(readOnly = true)
	public UserInfoResponse getUserInfoByToken(String token) {
		if (!jwtTokenProvider.validateToken(token)) {
			throw new InvalidAccessTokenException();
		}
		String userName = jwtTokenProvider.getUserName(token);

		User user = userRepository.findByUsername(userName).orElseThrow(InvalidAccessTokenException::new);

		return UserInfoResponse.of(token, user);
	}

	public Token reissueToken(String token) {
		if (!jwtTokenProvider.validateToken(token)) {
			throw new InvalidRefreshTokenException();
		}
		RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
			.orElseThrow(InvalidRefreshTokenException::new);
		User user = userRepository.findById(refreshToken.getUserId()).orElseThrow(InvalidRefreshTokenException::new);

		Token newToken = jwtTokenProvider.createToken(new UserDetail(user));

		refreshToken.updateToken(newToken.getRefreshToken());
		refreshTokenRepository.save(refreshToken);

		return newToken;
	}
}
