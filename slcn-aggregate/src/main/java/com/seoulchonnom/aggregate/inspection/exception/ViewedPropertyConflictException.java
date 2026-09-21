package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class ViewedPropertyConflictException extends BusinessException {
	public ViewedPropertyConflictException() {
		super(ErrorCode.VIEWED_PROPERTY_CONFLICT);
	}

	public ViewedPropertyConflictException(String message) {
		super(ErrorCode.VIEWED_PROPERTY_CONFLICT, message);
	}
}
