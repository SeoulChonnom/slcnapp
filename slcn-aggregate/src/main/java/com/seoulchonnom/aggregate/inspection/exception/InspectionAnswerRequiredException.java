package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InspectionAnswerRequiredException extends BusinessException {
	public InspectionAnswerRequiredException() {
		super(ErrorCode.INSPECTION_ANSWER_REQUIRED);
	}

	public InspectionAnswerRequiredException(String message) {
		super(ErrorCode.INSPECTION_ANSWER_REQUIRED, message);
	}
}
