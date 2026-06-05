package com.seoulchonnom.aggregate.common.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InternalServerErrorException extends BusinessException {
	public InternalServerErrorException(ErrorCode errorCode) {
		super(errorCode);
	}

	public InternalServerErrorException(String message) {
		super(ErrorCode.INTERNAL_SERVER_ERROR, message);
	}
}
