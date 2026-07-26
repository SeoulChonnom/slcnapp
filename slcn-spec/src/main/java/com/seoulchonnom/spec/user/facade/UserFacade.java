package com.seoulchonnom.spec.user.facade;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.user.facade.sdo.UserCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserLoginCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserPasswordVerifyCdo;
import com.seoulchonnom.spec.user.facade.sdo.UserProfileRdo;
import com.seoulchonnom.spec.user.facade.sdo.UserProfileUdo;
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

	@SecurityRequirement(name = "X-AUTH-TOKEN")
	@Operation(summary = "내 프로필 조회", description = "현재 로그인한 사용자의 프로필을 조회합니다.")
	ResponseEntity<UserProfileRdo> getCurrentUser(String userId);

	@SecurityRequirement(name = "X-AUTH-TOKEN")
	@Operation(summary = "프로필 수정 비밀번호 확인", description = "프로필 수정 전에 현재 비밀번호를 확인합니다.")
	ResponseEntity<Void> verifyCurrentUserPassword(String userId, UserPasswordVerifyCdo userPasswordVerifyCdo);

	@SecurityRequirement(name = "X-AUTH-TOKEN")
	@Operation(summary = "내 프로필 수정", description = "비밀번호 확인 후 이름, 비밀번호, 프로필 이미지를 수정합니다. 비밀번호 변경 시 현재 기기의 refresh 세션만 재발급됩니다.")
	ResponseEntity<UserProfileRdo> updateCurrentUser(String userId, UserProfileUdo userProfileUdo);
}
