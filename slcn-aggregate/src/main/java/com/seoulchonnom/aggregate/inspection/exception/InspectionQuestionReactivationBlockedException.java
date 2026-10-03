package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InspectionQuestionReactivationBlockedException extends BusinessException {
	public InspectionQuestionReactivationBlockedException() {
		super(ErrorCode.INSPECTION_QUESTION_REACTIVATION_BLOCKED);
	}

	public InspectionQuestionReactivationBlockedException(String message) {
		super(ErrorCode.INSPECTION_QUESTION_REACTIVATION_BLOCKED, message);
	}
}
