package com.seoulchonnom.aggregate.user.exception;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class UserLoginFailCountOverException extends BadRequestException {
	public UserLoginFailCountOverException() {
		super(ErrorCode.USER_LOGIN_FAIL_COUNT_OVER);
	}
}
