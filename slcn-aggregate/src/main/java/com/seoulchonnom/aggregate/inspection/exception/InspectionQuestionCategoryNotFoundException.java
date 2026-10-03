package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InspectionQuestionCategoryNotFoundException extends BusinessException {
	public InspectionQuestionCategoryNotFoundException() {
		super(ErrorCode.INSPECTION_QUESTION_CATEGORY_NOT_FOUND);
	}

	public InspectionQuestionCategoryNotFoundException(String message) {
		super(ErrorCode.INSPECTION_QUESTION_CATEGORY_NOT_FOUND, message);
	}
}
