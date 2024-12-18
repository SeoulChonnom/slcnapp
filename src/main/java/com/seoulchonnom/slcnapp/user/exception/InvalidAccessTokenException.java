package com.seoulchonnom.slcnapp.user.exception;

import static com.seoulchonnom.slcnapp.user.UserConstant.*;

import com.seoulchonnom.slcnapp.common.exception.InternalServerErrorException;

public class InvalidAccessTokenException extends InternalServerErrorException {
	public InvalidAccessTokenException() {
		super(ACCESS_TOKEN_INVALID_ERROR_MESSAGE);
	}
}