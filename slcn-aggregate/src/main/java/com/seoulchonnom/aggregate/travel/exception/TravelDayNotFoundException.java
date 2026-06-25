package com.seoulchonnom.aggregate.travel.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class TravelDayNotFoundException extends BusinessException {
	public TravelDayNotFoundException() {
		super(ErrorCode.TRAVEL_DAY_NOT_FOUND);
	}
}
