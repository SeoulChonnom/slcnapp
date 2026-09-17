package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InvalidInspectionOrderException extends BusinessException {
	public InvalidInspectionOrderException() {
		super(ErrorCode.INVALID_INSPECTION_ORDER);
	}

	public InvalidInspectionOrderException(String message) {
		super(ErrorCode.INVALID_INSPECTION_ORDER, message);
	}
}
