package com.seoulchonnom.slcnapp.user.controller;

import static com.seoulchonnom.slcnapp.user.UserConstant.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.slcnapp.common.dto.BaseResponse;
import com.seoulchonnom.slcnapp.user.dto.Token;
import com.seoulchonnom.slcnapp.user.dto.UserLoginRequest;
import com.seoulchonnom.slcnapp.user.dto.UserRegisterRequest;
import com.seoulchonnom.slcnapp.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "회원 관리 API", description = "회원 가입 및 로그인")
public class UserController {

	private final UserService userService;

	@Parameters({
		@Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)
	})
	@PostMapping("/register")
	@Operation(summary = "회원 가입", description = "회원 가입 API")
	public ResponseEntity<BaseResponse> registerUser(@RequestBody
	UserRegisterRequest userRegisterRequest) {
		userService.registerUser(userRegisterRequest);
		return new ResponseEntity<>(
			BaseResponse.from(true, USER_REGISTER_SUCCESS_MESSAGE),
			HttpStatus.OK);
	}

	@PostMapping("/login")
	@Operation(summary = "로그인", description = "로그인 API")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "Success")
	})
	public ResponseEntity<BaseResponse> loginUser(HttpServletResponse response,
		@RequestBody
		UserLoginRequest userLoginRequest) {
		Token token = userService.issueToken(userLoginRequest);

		userService.updateCookie(response, token.getRefreshToken());

		return new ResponseEntity<>(
			BaseResponse.from(true, USER_LOGIN_SUCCESS_MESSAGE, userService.getUserInfoByToken(token.getAccessToken())),
			HttpStatus.OK);
	}

	@GetMapping("/token")
	@Operation(summary = "토큰 갱신", description = "RefreshToken 갱신")
	public ResponseEntity<BaseResponse> reissueToken(@CookieValue(value = "refreshToken", required = false)
	String refreshToken, HttpServletResponse response) {
		Token token = userService.reissueToken(refreshToken);

		userService.updateCookie(response, token.getRefreshToken());

		return new ResponseEntity<>(
			BaseResponse.from(true, USER_LOGIN_SUCCESS_MESSAGE, userService.getUserInfoByToken(token.getAccessToken())),
			HttpStatus.OK);
	}
}
