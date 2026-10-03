package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InspectionQuestionCategoryInUseException extends BusinessException {
	public InspectionQuestionCategoryInUseException() {
		super(ErrorCode.INSPECTION_QUESTION_CATEGORY_IN_USE);
	}

	public InspectionQuestionCategoryInUseException(String message) {
		super(ErrorCode.INSPECTION_QUESTION_CATEGORY_IN_USE, message);
	}
}
