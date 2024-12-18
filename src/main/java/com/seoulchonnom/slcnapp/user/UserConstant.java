package com.seoulchonnom.slcnapp.user;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserConstant {
	public static final int COOKIE_EXPIRE_TIME = 60 * 60 * 24 * 14;

	public static final String USERNAME_NOT_FOUND_ERROR_MESSAGE = "해당하는 ID의 유저가 없습니다.";

	public static final String USER_REGISTER_SUCCESS_MESSAGE = "회원가입에 성공하였습니다.";
	public static final String USER_LOGIN_SUCCESS_MESSAGE = "로그인에 성공하였습니다.";

	public static final String ACCESS_TOKEN_INVALID_ERROR_MESSAGE = "인증 정보가 잘못 되었습니다.";
	public static final String ACCESS_ROLE_MISSING_ERROR_MESSAGE = "접근 권한이 없습니다.";
	public static final String ACCESS_ROLE_DENIED_ERROR_MESSAGE = "접근 권한이 부족합니다.";
	public static final String REFRESH_TOKEN_INVALID_ERROR_MESSAGE = "리프레쉬 토큰이 유효하지 않습니다.";
}
