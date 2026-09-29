package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InspectionQuestionCategoryRequiredException extends BusinessException {
	public InspectionQuestionCategoryRequiredException() {
		super(ErrorCode.INSPECTION_QUESTION_CATEGORY_REQUIRED);
	}

	public InspectionQuestionCategoryRequiredException(String message) {
		super(ErrorCode.INSPECTION_QUESTION_CATEGORY_REQUIRED, message);
	}
}
