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

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {

	private final UserService userService;

	@PostMapping("/register")
	public ResponseEntity<BaseResponse> registerUser(@RequestBody UserRegisterRequest userRegisterRequest) {
		userService.registerUser(userRegisterRequest);
		return new ResponseEntity<>(BaseResponse.from(true, USER_REGISTER_SUCCESS_MESSAGE), HttpStatus.OK);
	}

	@PostMapping("/login")
	public ResponseEntity<BaseResponse> loginUser(HttpServletResponse response,
		@RequestBody UserLoginRequest userLoginRequest) {
		Token token = userService.issueToken(userLoginRequest);

		userService.updateCookie(response, token.getRefreshToken());

		return new ResponseEntity<>(BaseResponse.from(true, USER_LOGIN_SUCCESS_MESSAGE, userService.getUserInfo(token)),
			HttpStatus.OK);
	}

	@GetMapping("/token")
	public ResponseEntity<BaseResponse> reissueToken(
		@CookieValue(value = "refreshToken", required = false) String refreshToken, HttpServletResponse response) {
		Token token = userService.reissueToken(refreshToken);

		userService.updateCookie(response, token.getRefreshToken());

		return new ResponseEntity<>(BaseResponse.from(true, USER_LOGIN_SUCCESS_MESSAGE, userService.getUserInfo(token)),
			HttpStatus.OK);
	}
}
