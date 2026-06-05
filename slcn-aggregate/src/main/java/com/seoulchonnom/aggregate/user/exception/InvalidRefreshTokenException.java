package com.seoulchonnom.aggregate.user.exception;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InvalidRefreshTokenException extends BadRequestException {
	public InvalidRefreshTokenException() {
		super(ErrorCode.INVALID_REFRESH_TOKEN);
	}
}
