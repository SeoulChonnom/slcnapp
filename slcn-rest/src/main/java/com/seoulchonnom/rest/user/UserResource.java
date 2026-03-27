package com.seoulchonnom.rest.user;

import static com.seoulchonnom.spec.user.constant.UserConstant.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/user")
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

		return new ResponseEntity<>(userSessionVo.getUserRdo(), HttpStatus.OK);
	}

	@Override
	@GetMapping("/token")
	public ResponseEntity<UserRdo> reissueToken(@CookieValue(AuthConstant.REFRESH_TOKEN_COOKIE_NAME) String refreshToken,
		HttpServletResponse response) {
		UserSessionVo userSessionVo = userFlow.reissue(refreshToken);
		addRefreshTokenCookie(response, userSessionVo.getTokenRdo().getRefreshToken());
		return new ResponseEntity<>(userSessionVo.getUserRdo(), HttpStatus.OK);
	}

	private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
		ResponseCookie refreshTokenCookie = ResponseCookie.from(AuthConstant.REFRESH_TOKEN_COOKIE_NAME, refreshToken)
			.httpOnly(true)
			.secure(refreshCookieSecure)
			.path("/")
			.sameSite(refreshCookieSameSite)
			.maxAge(refreshCookieMaxAge)
			.build();

		response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());
	}
}
