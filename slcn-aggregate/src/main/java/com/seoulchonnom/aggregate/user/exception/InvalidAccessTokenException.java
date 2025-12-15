package com.seoulchonnom.aggregate.user.exception;

import static com.seoulchonnom.spec.user.constant.UserConstant.*;

import com.seoulchonnom.aggregate.common.exception.InternalServerErrorException;

public class InvalidAccessTokenException extends InternalServerErrorException {
	public InvalidAccessTokenException() {
		super(ACCESS_TOKEN_INVALID_ERROR_MESSAGE);
	}
}