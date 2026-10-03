package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InspectionQuestionCategoryConflictException extends BusinessException {
	public InspectionQuestionCategoryConflictException() {
		super(ErrorCode.INSPECTION_QUESTION_CATEGORY_CONFLICT);
	}

	public InspectionQuestionCategoryConflictException(String message) {
		super(ErrorCode.INSPECTION_QUESTION_CATEGORY_CONFLICT, message);
	}
}
