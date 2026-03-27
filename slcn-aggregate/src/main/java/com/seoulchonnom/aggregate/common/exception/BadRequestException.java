package com.seoulchonnom.aggregate.common.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class BadRequestException extends BusinessException {
	public BadRequestException(ErrorCode errorCode) {
		super(errorCode);
	}

	public BadRequestException(String message) {
		super(ErrorCode.BAD_REQUEST, message);
	}
}
