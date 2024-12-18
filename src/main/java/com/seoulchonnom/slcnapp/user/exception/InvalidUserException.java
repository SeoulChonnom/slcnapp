package com.seoulchonnom.slcnapp.user.exception;

import static com.seoulchonnom.slcnapp.user.UserConstant.*;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

public class InvalidUserException extends BadRequestException {
	public InvalidUserException() {
		super(USERNAME_NOT_FOUND_ERROR_MESSAGE);
	}
}
