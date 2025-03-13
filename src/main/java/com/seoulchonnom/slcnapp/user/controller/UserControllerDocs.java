package com.seoulchonnom.slcnapp.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;

import com.seoulchonnom.slcnapp.common.dto.BaseResponse;
import com.seoulchonnom.slcnapp.user.dto.UserLoginRequest;
import com.seoulchonnom.slcnapp.user.dto.UserRegisterRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "회원 관리 API", description = "회원 가입 및 로그인")
public interface UserControllerDocs {
	@Parameters({
		@Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)})
	@Operation(summary = "회원 가입", description = "회원 가입 API")
	ResponseEntity<BaseResponse> registerUser(UserRegisterRequest userRegisterRequest);

	@Operation(summary = "로그인", description = "로그인 API")
	@ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Success")})
	ResponseEntity<BaseResponse> loginUser(HttpServletResponse response, UserLoginRequest userLoginRequest);

	@Operation(summary = "토큰 갱신", description = "RefreshToken 갱신")
	ResponseEntity<BaseResponse> reissueToken(String refreshToken, HttpServletResponse response);
}
