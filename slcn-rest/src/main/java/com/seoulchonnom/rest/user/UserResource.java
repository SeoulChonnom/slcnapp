package com.seoulchonnom.rest.user;

import static com.seoulchonnom.spec.user.constant.UserConstant.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.auth.constant.AuthConstant;
import com.seoulchonnom.auth.flow.UserFlow;
import com.seoulchonnom.auth.flow.vo.UserSessionVo;
import com.seoulchonnom.spec.user.facade.UserFacade;
import com.seoulchonnom.spec.user.facade.sdo.UserCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserLoginCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserRdo;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserResource implements UserFacade {

	private final UserFlow userFlow;

	@Value("${cookie.expire.time}")
	private long refreshCookieMaxAge;

	@Value("${cookie.secure:false}")
	private boolean refreshCookieSecure;

	@Value("${cookie.sameSite:Lax}")
	private String refreshCookieSameSite;

	@Override
	@PostMapping("/register")
	public ResponseEntity<String> registerUser(@RequestBody UserCdo userCdo) {
		userFlow.registerUser(userCdo);
		return new ResponseEntity<>(USER_REGISTER_SUCCESS_MESSAGE, HttpStatus.OK);
	}

	@Override
	@PostMapping("/login")
	public ResponseEntity<UserRdo> loginUser(HttpServletResponse response, @RequestBody UserLoginCdo userLoginCdo) {
		UserSessionVo userSessionVo = userFlow.login(userLoginCdo);
		addRefreshTokenCookie(response, userSessionVo.getTokenRdo().getRefreshToken());
		addSessionIdCookie(response, userSessionVo.getSessionId());

		return new ResponseEntity<>(userSessionVo.getUserRdo(), HttpStatus.OK);
	}

	@Override
	@PostMapping("/token")
	public ResponseEntity<UserRdo> reissueToken(
		@CookieValue(value = AuthConstant.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
		@CookieValue(value = AuthConstant.SESSION_ID_COOKIE_NAME, required = false) String sessionId,
		HttpServletResponse response) {
		UserSessionVo userSessionVo = userFlow.reissue(refreshToken, sessionId);
		addRefreshTokenCookie(response, userSessionVo.getTokenRdo().getRefreshToken());
		addSessionIdCookie(response, userSessionVo.getSessionId());
		return new ResponseEntity<>(userSessionVo.getUserRdo(), HttpStatus.OK);
	}

	@Override
	@PostMapping("/logout")
	public ResponseEntity<Void> logoutUser(
		@CookieValue(value = AuthConstant.SESSION_ID_COOKIE_NAME, required = false) String sessionId,
		HttpServletResponse response
	) {
		userFlow.logout(sessionId);
		expireCookie(response, AuthConstant.REFRESH_TOKEN_COOKIE_NAME);
		expireCookie(response, AuthConstant.SESSION_ID_COOKIE_NAME);
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
		ResponseCookie refreshTokenCookie = buildCookie(AuthConstant.REFRESH_TOKEN_COOKIE_NAME, refreshToken, refreshCookieMaxAge);
		response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());
	}

	private void addSessionIdCookie(HttpServletResponse response, String sessionId) {
		ResponseCookie sessionIdCookie = buildCookie(AuthConstant.SESSION_ID_COOKIE_NAME, sessionId, refreshCookieMaxAge);
		response.addHeader(HttpHeaders.SET_COOKIE, sessionIdCookie.toString());
	}

	private void expireCookie(HttpServletResponse response, String cookieName) {
		ResponseCookie expiredCookie = buildCookie(cookieName, "", 0);
		response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
	}

	private ResponseCookie buildCookie(String name, String value, long maxAge) {
		return ResponseCookie.from(name, value)
			.httpOnly(true)
			.secure(refreshCookieSecure)
			.path("/")
			.sameSite(refreshCookieSameSite)
			.maxAge(maxAge)
			.build();
	}
}
