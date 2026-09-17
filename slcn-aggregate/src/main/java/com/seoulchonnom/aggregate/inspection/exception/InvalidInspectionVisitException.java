package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InvalidInspectionVisitException extends BusinessException {
	public InvalidInspectionVisitException() {
		super(ErrorCode.INVALID_INSPECTION_VISIT);
	}

	public InvalidInspectionVisitException(String message) {
		super(ErrorCode.INVALID_INSPECTION_VISIT, message);
	}
}
