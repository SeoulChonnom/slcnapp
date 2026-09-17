package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InvalidInspectionQuestionException extends BusinessException {
	public InvalidInspectionQuestionException() {
		super(ErrorCode.INVALID_INSPECTION_QUESTION);
	}

	public InvalidInspectionQuestionException(String message) {
		super(ErrorCode.INVALID_INSPECTION_QUESTION, message);
	}
}
