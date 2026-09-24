package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InspectionAreaDuplicatedException extends BusinessException {
	public InspectionAreaDuplicatedException() {
		super(ErrorCode.INSPECTION_AREA_DUPLICATED);
	}

	public InspectionAreaDuplicatedException(String message) {
		super(ErrorCode.INSPECTION_AREA_DUPLICATED, message);
	}
}
