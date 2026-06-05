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
}
