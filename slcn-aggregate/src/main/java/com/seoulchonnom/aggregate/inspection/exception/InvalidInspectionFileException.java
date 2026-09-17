package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InvalidInspectionFileException extends BusinessException {
	public InvalidInspectionFileException() {
		super(ErrorCode.INVALID_INSPECTION_FILE);
	}

	public InvalidInspectionFileException(String message) {
		super(ErrorCode.INVALID_INSPECTION_FILE, message);
	}
}
