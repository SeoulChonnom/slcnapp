package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InspectionAreaNotFoundException extends BusinessException {
	public InspectionAreaNotFoundException() {
		super(ErrorCode.INSPECTION_AREA_NOT_FOUND);
	}

	public InspectionAreaNotFoundException(String message) {
		super(ErrorCode.INSPECTION_AREA_NOT_FOUND, message);
	}
}
