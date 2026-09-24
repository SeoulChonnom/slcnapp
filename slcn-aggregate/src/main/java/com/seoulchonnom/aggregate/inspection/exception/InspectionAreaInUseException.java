package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InspectionAreaInUseException extends BusinessException {
	public InspectionAreaInUseException() {
		super(ErrorCode.INSPECTION_AREA_IN_USE);
	}

	public InspectionAreaInUseException(String message) {
		super(ErrorCode.INSPECTION_AREA_IN_USE, message);
	}
}
