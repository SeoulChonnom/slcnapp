package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InspectionQuestionCategoryDuplicatedException extends BusinessException {
	public InspectionQuestionCategoryDuplicatedException() {
		super(ErrorCode.INSPECTION_QUESTION_CATEGORY_DUPLICATED);
	}

	public InspectionQuestionCategoryDuplicatedException(String message) {
		super(ErrorCode.INSPECTION_QUESTION_CATEGORY_DUPLICATED, message);
	}
}
