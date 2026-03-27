package com.seoulchonnom.aggregate.user.exception;

import com.seoulchonnom.aggregate.common.exception.InternalServerErrorException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InvalidAccessTokenException extends InternalServerErrorException {
	public InvalidAccessTokenException() {
		super(ErrorCode.INVALID_ACCESS_TOKEN);
	}
}
