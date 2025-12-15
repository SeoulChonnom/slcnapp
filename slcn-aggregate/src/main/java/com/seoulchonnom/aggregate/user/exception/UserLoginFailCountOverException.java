package com.seoulchonnom.aggregate.user.exception;

import static com.seoulchonnom.spec.user.constant.UserConstant.*;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;

public class UserLoginFailCountOverException extends BadRequestException {
	public UserLoginFailCountOverException() {
		super(USER_LOGIN_FAIL_COUNT_OVER_ERROR_MESSAGE);
	}
}
