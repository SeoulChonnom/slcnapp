package com.seoulchonnom.slcnapp.user;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserConstant {
	public static final String INVALID_USER_LOGIN_REQUEST_MESSAGE = "잘못된 아이디 혹은 비밀번호 입니다..";

	public static final String USER_REGISTER_SUCCESS_MESSAGE = "회원가입에 성공하였습니다.";
	public static final String USER_LOGIN_SUCCESS_MESSAGE = "로그인에 성공하였습니다.";

}
