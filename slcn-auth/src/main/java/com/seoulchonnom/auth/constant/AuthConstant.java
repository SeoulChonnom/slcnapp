package com.seoulchonnom.auth.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuthConstant {
	public static final String ACCESS_TOKEN_HEADER_NAME = "X-AUTH-TOKEN";
	public static final String AUTHORIZATION_HEADER_NAME = "Authorization";
	public static final String BEARER_PREFIX = "Bearer ";
	public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
	public static final String SESSION_ID_COOKIE_NAME = "sessionId";
	/**
	 * sessionId 쿠키만으로 인증된 요청에 부여하는 권한이다.
	 * USER를 주지 않으므로 이미지 조회 외의 endpoint에는 도달할 수 없다.
	 */
	public static final String IMAGE_READ_AUTHORITY = "IMAGE_READ";
}
