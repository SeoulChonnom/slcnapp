package com.seoulchonnom.rest.user;

import static com.seoulchonnom.spec.user.constant.UserConstant.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.auth.constant.AuthConstant;
import com.seoulchonnom.auth.flow.UserFlow;
import com.seoulchonnom.auth.flow.vo.TokenSessionVo;
import com.seoulchonnom.auth.flow.vo.UserProfileSessionVo;
import com.seoulchonnom.auth.flow.vo.UserSessionVo;
import com.seoulchonnom.spec.user.facade.UserFacade;
import com.seoulchonnom.spec.user.facade.sdo.UserCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserLoginCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserPasswordVerifyCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserProfileRdo;
import com.seoulchonnom.spec.user.facade.sdo.UserProfileUdo;
import com.seoulchonnom.spec.user.facade.sdo.UserRdo;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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

	@Override
	@GetMapping("/me")
	public ResponseEntity<UserProfileRdo> getCurrentUser(
		@AuthenticationPrincipal(expression = "user.id") String userId
	) {
		return new ResponseEntity<>(userFlow.getCurrentUser(userId), HttpStatus.OK);
	}

	@Override
	@PostMapping("/me/password/verify")
	public ResponseEntity<Void> verifyCurrentUserPassword(
		@AuthenticationPrincipal(expression = "user.id") String userId,
		@RequestBody @Valid UserPasswordVerifyCdo userPasswordVerifyCdo
	) {
		userFlow.verifyCurrentUserPassword(userId, userPasswordVerifyCdo);
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@Override
	@PutMapping("/me")
	public ResponseEntity<UserProfileRdo> updateCurrentUser(
		@AuthenticationPrincipal(expression = "user.id") String userId,
		@RequestBody @Valid UserProfileUdo userProfileUdo
	) {
		UserProfileSessionVo userProfileSessionVo = userFlow.updateCurrentUser(userId, userProfileUdo);
		TokenSessionVo tokenSessionVo = userProfileSessionVo.tokenSessionVo();
		if (tokenSessionVo == null) {
			return new ResponseEntity<>(userProfileSessionVo.userProfileRdo(), HttpStatus.OK);
		}

		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.SET_COOKIE, refreshTokenCookie(tokenSessionVo.tokenRdo().getRefreshToken()).toString());
		headers.add(HttpHeaders.SET_COOKIE, sessionIdCookie(tokenSessionVo.sessionId()).toString());
		return new ResponseEntity<>(userProfileSessionVo.userProfileRdo(), headers, HttpStatus.OK);
	}

	private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
		response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie(refreshToken).toString());
	}

	private void addSessionIdCookie(HttpServletResponse response, String sessionId) {
		response.addHeader(HttpHeaders.SET_COOKIE, sessionIdCookie(sessionId).toString());
	}

	private ResponseCookie refreshTokenCookie(String refreshToken) {
		return buildCookie(AuthConstant.REFRESH_TOKEN_COOKIE_NAME, refreshToken, refreshCookieMaxAge);
	}

	private ResponseCookie sessionIdCookie(String sessionId) {
		return buildCookie(AuthConstant.SESSION_ID_COOKIE_NAME, sessionId, refreshCookieMaxAge);
	}

	private void expireCookie(HttpServletResponse response, String cookieName) {
		response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie(cookieName).toString());
	}

	private ResponseCookie expiredCookie(String cookieName) {
		return buildCookie(cookieName, "", 0);
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
