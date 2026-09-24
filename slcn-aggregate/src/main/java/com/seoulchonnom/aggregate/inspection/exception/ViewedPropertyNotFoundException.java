package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class ViewedPropertyNotFoundException extends BusinessException {
	public ViewedPropertyNotFoundException() {
		super(ErrorCode.VIEWED_PROPERTY_NOT_FOUND);
	}

	public ViewedPropertyNotFoundException(String message) {
		super(ErrorCode.VIEWED_PROPERTY_NOT_FOUND, message);
	}
}
