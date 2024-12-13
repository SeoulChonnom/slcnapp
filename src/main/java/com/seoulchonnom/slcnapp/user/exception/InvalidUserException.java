package com.seoulchonnom.slcnapp.user.exception;

import static com.seoulchonnom.slcnapp.user.UserConstant.*;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

public class InvalidUserException extends BadRequestException {
	public InvalidUserException() {
		super(INVALID_USER_LOGIN_REQUEST_MESSAGE);
	}
}
