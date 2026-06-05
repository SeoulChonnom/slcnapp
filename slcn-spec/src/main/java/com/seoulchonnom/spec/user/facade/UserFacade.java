package com.seoulchonnom.spec.user.facade;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.user.facade.sdo.UserCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserLoginCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserRdo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "회원 관리 API", description = "회원 가입 및 로그인")
public interface UserFacade {
	@SecurityRequirement(name = "X-AUTH-TOKEN")
	@Operation(summary = "회원 가입", description = "회원 가입 API")
	ResponseEntity<String> registerUser(UserCdo userCdo);

	@Operation(summary = "로그인", description = "로그인 API")
	@ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Success")})
	ResponseEntity<UserRdo> loginUser(HttpServletResponse response, UserLoginCdo userLoginCdo);

	@Operation(summary = "토큰 갱신", description = "RefreshToken 갱신")
	ResponseEntity<UserRdo> reissueToken(String refreshToken, String sessionId, HttpServletResponse response);

	@Operation(summary = "로그아웃", description = "현재 세션 로그아웃")
	ResponseEntity<Void> logoutUser(String sessionId, HttpServletResponse response);
}
