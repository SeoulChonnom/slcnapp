package com.seoulchonnom.slcnapp.user.exception;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

import static com.seoulchonnom.slcnapp.user.UserConstant.REFRESH_TOKEN_INVALID_ERROR_MESSAGE;

public class InvalidRefreshTokenException extends BadRequestException {
	public InvalidRefreshTokenException() {
		super(REFRESH_TOKEN_INVALID_ERROR_MESSAGE);
	}
}
