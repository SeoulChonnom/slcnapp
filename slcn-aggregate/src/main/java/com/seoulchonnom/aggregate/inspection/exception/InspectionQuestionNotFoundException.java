package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InspectionQuestionNotFoundException extends BusinessException {
	public InspectionQuestionNotFoundException() {
		super(ErrorCode.INSPECTION_QUESTION_NOT_FOUND);
	}

	public InspectionQuestionNotFoundException(String message) {
		super(ErrorCode.INSPECTION_QUESTION_NOT_FOUND, message);
	}
}
