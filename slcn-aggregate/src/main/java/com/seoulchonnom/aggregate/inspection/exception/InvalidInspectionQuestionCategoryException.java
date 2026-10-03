package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InvalidInspectionQuestionCategoryException extends BusinessException {
	public InvalidInspectionQuestionCategoryException() {
		super(ErrorCode.INVALID_INSPECTION_QUESTION_CATEGORY);
	}

	public InvalidInspectionQuestionCategoryException(String message) {
		super(ErrorCode.INVALID_INSPECTION_QUESTION_CATEGORY, message);
	}
}
