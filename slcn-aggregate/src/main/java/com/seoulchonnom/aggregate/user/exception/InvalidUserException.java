package com.seoulchonnom.aggregate.user.exception;

import static com.seoulchonnom.spec.user.constant.UserConstant.*;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;

public class InvalidUserException extends BadRequestException {
	public InvalidUserException() {
		super(USERNAME_NOT_FOUND_ERROR_MESSAGE);
	}
}
