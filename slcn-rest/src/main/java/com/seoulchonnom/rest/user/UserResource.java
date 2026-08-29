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
	private static final String ROOT_COOKIE_PATH = "/";
	/** 이 컨트롤러의 @RequestMapping과 같아야 한다. 리프레시 토큰 쿠키를 이 하위로만 내보낸다. */
	private static final String REFRESH_TOKEN_COOKIE_PATH_SUFFIX = "/users";

	private final UserFlow userFlow;

	@Value("${cookie.expire.time}")
	private long refreshCookieMaxAge;

	@Value("${cookie.secure:true}")
	private boolean refreshCookieSecure;

	@Value("${cookie.sameSite:Lax}")
	private String refreshCookieSameSite;

	@Value("${server.servlet.context-path:}")
	private String contextPath;

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
		expireRefreshTokenCookie(response);
		expireSessionIdCookie(response);
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
		headers.add(HttpHeaders.SET_COOKIE, legacyRefreshTokenCookie().toString());
		headers.add(HttpHeaders.SET_COOKIE, sessionIdCookie(tokenSessionVo.sessionId()).toString());
		return new ResponseEntity<>(userProfileSessionVo.userProfileRdo(), headers, HttpStatus.OK);
	}

	private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
		response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie(refreshToken).toString());
		response.addHeader(HttpHeaders.SET_COOKIE, legacyRefreshTokenCookie().toString());
	}

	private void addSessionIdCookie(HttpServletResponse response, String sessionId) {
		response.addHeader(HttpHeaders.SET_COOKIE, sessionIdCookie(sessionId).toString());
	}

	/**
	 * 리프레시 토큰은 재발급/로그아웃에서만 쓰므로 쿠키 경로를 /users 하위로 좁힌다.
	 * path=/로 두면 이미지 조회를 포함한 모든 요청에 14일짜리 토큰이 함께 실려 나간다.
	 */
	private ResponseCookie refreshTokenCookie(String refreshToken) {
		return buildCookie(AuthConstant.REFRESH_TOKEN_COOKIE_NAME, refreshToken, refreshCookieMaxAge,
			refreshTokenCookiePath());
	}

	/**
	 * 과거에 path=/로 발급해둔 리프레시 토큰 쿠키를 만료시킨다.
	 * 지우지 않으면 좁힌 쿠키와 함께 전송되어 경로를 좁힌 효과가 사라진다.
	 * 기존 쿠키 수명(cookie.expire.time)이 모두 지난 뒤에는 제거해도 된다.
	 */
	private ResponseCookie legacyRefreshTokenCookie() {
		return buildCookie(AuthConstant.REFRESH_TOKEN_COOKIE_NAME, "", 0, ROOT_COOKIE_PATH);
	}

	/**
	 * sessionId는 이미지 조회(/assets/**)에서도 필요하므로 경로를 좁히지 않는다.
	 */
	private ResponseCookie sessionIdCookie(String sessionId) {
		return buildCookie(AuthConstant.SESSION_ID_COOKIE_NAME, sessionId, refreshCookieMaxAge, ROOT_COOKIE_PATH);
	}

	private void expireRefreshTokenCookie(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE,
			buildCookie(AuthConstant.REFRESH_TOKEN_COOKIE_NAME, "", 0, refreshTokenCookiePath()).toString());
		response.addHeader(HttpHeaders.SET_COOKIE, legacyRefreshTokenCookie().toString());
	}

	private void expireSessionIdCookie(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE,
			buildCookie(AuthConstant.SESSION_ID_COOKIE_NAME, "", 0, ROOT_COOKIE_PATH).toString());
	}

	private String refreshTokenCookiePath() {
		return (contextPath == null ? "" : contextPath) + REFRESH_TOKEN_COOKIE_PATH_SUFFIX;
	}

	private ResponseCookie buildCookie(String name, String value, long maxAge, String path) {
		return ResponseCookie.from(name, value)
			.httpOnly(true)
			.secure(refreshCookieSecure)
			.path(path)
			.sameSite(refreshCookieSameSite)
			.maxAge(maxAge)
			.build();
	}
}
