package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InspectionVisitNotFoundException extends BusinessException {
	public InspectionVisitNotFoundException() {
		super(ErrorCode.INSPECTION_VISIT_NOT_FOUND);
	}

	public InspectionVisitNotFoundException(String message) {
		super(ErrorCode.INSPECTION_VISIT_NOT_FOUND, message);
	}
}
