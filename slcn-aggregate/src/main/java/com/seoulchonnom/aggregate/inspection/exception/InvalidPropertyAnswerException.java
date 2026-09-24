package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InvalidPropertyAnswerException extends BusinessException {
	public InvalidPropertyAnswerException() {
		super(ErrorCode.INVALID_PROPERTY_ANSWER);
	}

	public InvalidPropertyAnswerException(String message) {
		super(ErrorCode.INVALID_PROPERTY_ANSWER, message);
	}
}
