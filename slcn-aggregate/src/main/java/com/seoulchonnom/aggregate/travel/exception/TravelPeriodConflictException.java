package com.seoulchonnom.aggregate.travel.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class TravelPeriodConflictException extends BusinessException {
	public TravelPeriodConflictException() {
		super(ErrorCode.TRAVEL_PERIOD_CONFLICT);
	}

	public TravelPeriodConflictException(String message) {
		super(ErrorCode.TRAVEL_PERIOD_CONFLICT, message);
	}
}
