package com.seoulchonnom.slcnapp.user.exception;

import static com.seoulchonnom.slcnapp.user.UserConstant.*;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

public class InvalidRefreshTokenException extends BadRequestException {
	public InvalidRefreshTokenException() {
		super(REFRESH_TOKEN_INVALID_ERROR_MESSAGE);
	}
}
