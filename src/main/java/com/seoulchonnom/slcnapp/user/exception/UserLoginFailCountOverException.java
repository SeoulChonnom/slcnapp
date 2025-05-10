package com.seoulchonnom.slcnapp.user.exception;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

import static com.seoulchonnom.slcnapp.user.UserConstant.USER_LOGIN_FAIL_COUNT_OVER_ERROR_MESSAGE;

public class UserLoginFailCountOverException extends BadRequestException {
	public UserLoginFailCountOverException() {
		super(USER_LOGIN_FAIL_COUNT_OVER_ERROR_MESSAGE);
	}
}
