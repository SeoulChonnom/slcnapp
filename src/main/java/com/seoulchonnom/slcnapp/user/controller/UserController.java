package com.seoulchonnom.slcnapp.user.controller;

import static com.seoulchonnom.slcnapp.user.UserConstant.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.slcnapp.common.dto.BaseResponse;
import com.seoulchonnom.slcnapp.user.dto.UserLoginRequest;
import com.seoulchonnom.slcnapp.user.dto.UserRegisterRequest;
import com.seoulchonnom.slcnapp.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "회원 관리 API", description = "회원 가입 및 로그인")
public class UserController {

	private final UserService userService;

	@PostMapping("/register")
	@Operation(summary = "회원 가입", description = "회원 가입 API")
	public ResponseEntity<BaseResponse> registerUser(@RequestBody UserRegisterRequest userRegisterRequest) {
		userService.registerUser(userRegisterRequest);
		return new ResponseEntity<>(BaseResponse.from(true, USER_REGISTER_SUCCESS_MESSAGE), HttpStatus.OK);
	}

	@PostMapping("/login")
	@Operation(summary = "로그인", description = "로그인 API")
	public ResponseEntity<BaseResponse> loginUser(@RequestBody UserLoginRequest userLoginRequest) {
		return new ResponseEntity<>(
			BaseResponse.from(true, USER_LOGIN_SUCCESS_MESSAGE, userService.loginUser(userLoginRequest)),
			HttpStatus.OK);
	}

}
