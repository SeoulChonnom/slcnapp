package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InspectionVisitConflictException extends BusinessException {
	public InspectionVisitConflictException() {
		super(ErrorCode.INSPECTION_VISIT_CONFLICT);
	}

	public InspectionVisitConflictException(String message) {
		super(ErrorCode.INSPECTION_VISIT_CONFLICT, message);
	}
}
