package com.seoulchonnom.aggregate.user.exception;

import static com.seoulchonnom.spec.user.constant.UserConstant.*;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;

public class InvalidRefreshTokenException extends BadRequestException {
	public InvalidRefreshTokenException() {
		super(REFRESH_TOKEN_INVALID_ERROR_MESSAGE);
	}
}
