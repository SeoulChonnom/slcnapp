package com.seoulchonnom.slcnapp.user.exception;

import static com.seoulchonnom.slcnapp.user.UserConstant.*;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

public class UserLoginFailCountOverException extends BadRequestException {
	public UserLoginFailCountOverException() {
		super(USER_LOGIN_FAIL_COUNT_OVER_ERROR_MESSAGE);
	}
}
