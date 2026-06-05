package com.seoulchonnom.aggregate.user.exception;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InvalidUserException extends BadRequestException {
	public InvalidUserException() {
		super(ErrorCode.INVALID_USER);
	}
}
