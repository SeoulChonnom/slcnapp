package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InspectionQuestionCategoryDisabledException extends BusinessException {
	public InspectionQuestionCategoryDisabledException() {
		super(ErrorCode.INSPECTION_QUESTION_CATEGORY_DISABLED);
	}

	public InspectionQuestionCategoryDisabledException(String message) {
		super(ErrorCode.INSPECTION_QUESTION_CATEGORY_DISABLED, message);
	}
}
