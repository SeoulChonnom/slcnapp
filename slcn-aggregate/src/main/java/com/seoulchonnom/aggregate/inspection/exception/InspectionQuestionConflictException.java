package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InspectionQuestionConflictException extends BusinessException {
	public InspectionQuestionConflictException() {
		super(ErrorCode.INSPECTION_QUESTION_CONFLICT);
	}

	public InspectionQuestionConflictException(String message) {
		super(ErrorCode.INSPECTION_QUESTION_CONFLICT, message);
	}
}
