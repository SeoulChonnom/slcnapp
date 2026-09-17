package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InvalidViewedPropertyException extends BusinessException {
	public InvalidViewedPropertyException() {
		super(ErrorCode.INVALID_VIEWED_PROPERTY);
	}

	public InvalidViewedPropertyException(String message) {
		super(ErrorCode.INVALID_VIEWED_PROPERTY, message);
	}
}
