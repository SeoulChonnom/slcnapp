package com.seoulchonnom.aggregate.user.exception;

import com.seoulchonnom.aggregate.common.exception.InternalServerErrorException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class UserLoginNotFoundException extends InternalServerErrorException {
	public UserLoginNotFoundException() {
		super(ErrorCode.USER_LOGIN_NOT_FOUND);
	}
}
