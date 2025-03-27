package com.seoulchonnom.slcnapp.user.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
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
import com.seoulchonnom.slcnapp.user.exception.UserLoginFailCountOverException;
import com.seoulchonnom.slcnapp.user.repository.AuthorityRepository;
import com.seoulchonnom.slcnapp.user.repository.RefreshTokenRepository;
import com.seoulchonnom.slcnapp.user.repository.UserRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;
	private final AuthorityRepository authorityRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;

	@Value("${cookie.expire.time}")
	private int COOKIE_EXPIRE_TIME;
	@Value("${login.fail.limit.count}")
	private int LOGIN_FAIL_LIMIT_COUNT;
	@Value("${login.limit.clear.time}")
	private int LOGIN_LIMIT_CLEAR_TIME;
	private final int LOGIN_ERROR_CODE = -1;

	@Transactional
	public void registerUser(UserRegisterRequest userRegisterRequest) {
		User user = User.builder()
			.name(userRegisterRequest.getName())
			.username(userRegisterRequest.getUserName())
			.password(passwordEncoder.encode(userRegisterRequest.getPassword()))
			.loginFailCount(0)
			.build();

		userRepository.save(user);

		Authority authority = Authority.builder().role(Role.USER).user(user).build();
		authorityRepository.save(authority);
	}

	@Transactional
	public Token issueToken(UserLoginRequest userLoginRequest) {
		User user = userRepository.findByUsername(userLoginRequest.getUsername())
			.orElseThrow(InvalidUserException::new);

		if (user.getLoginFailCount() >= LOGIN_FAIL_LIMIT_COUNT
			&& Duration.between(user.getLastLoginFailTime(), LocalDateTime.now()).getSeconds()
			> LOGIN_LIMIT_CLEAR_TIME) {
			throw new UserLoginFailCountOverException();
		}

		if (passwordEncoder.matches(userLoginRequest.getPassword(), user.getPassword())) {
			Token token = jwtTokenProvider.createToken(new UserDetail(user), user.getId());

			RefreshToken refreshToken = RefreshToken.builder().id(user.getId()).token(token.getRefreshToken()).build();
			refreshTokenRepository.save(refreshToken);

			user.resetLoginFailCount();

			return token;
		} else {
			user.updateLoginFailCount();
			return Token.builder().userId(LOGIN_ERROR_CODE).build();
		}
	}

	//	@Transactional(propagation = Propagation.REQUIRES_NEW)
	//    protected void updateLoginFailCount(User user) {
	//		user.updateLoginFailCount();
	//	}

	public void updateCookie(HttpServletResponse response, String token) {
		Cookie cookie = new Cookie("refreshToken", token);

		cookie.setSecure(true);
		cookie.setHttpOnly(true);
		cookie.setPath("/");
		cookie.setMaxAge(COOKIE_EXPIRE_TIME);

		response.addCookie(cookie);
	}

	public UserInfoResponse getUserInfo(Token token) {
		if (token.getUserId() == LOGIN_ERROR_CODE) {
			throw new InvalidUserException();
		}

		User user = userRepository.findById(token.getUserId()).orElseThrow(InvalidAccessTokenException::new);

		return UserInfoResponse.of(token.getAccessToken(), user);
	}

	@Transactional
	public Token reissueToken(String token) {
		if (!jwtTokenProvider.validateToken(token)) {
			throw new InvalidRefreshTokenException();
		}
		RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
			.orElseThrow(InvalidRefreshTokenException::new);
		User user = userRepository.findById(refreshToken.getId()).orElseThrow(InvalidRefreshTokenException::new);

		Token newToken = jwtTokenProvider.createToken(new UserDetail(user), user.getId());

		refreshToken.updateToken(newToken.getRefreshToken());
		refreshTokenRepository.save(refreshToken);

		return newToken;
	}
}
