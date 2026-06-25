package com.seoulchonnom.aggregate.travel.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class TravelNotFoundException extends BusinessException {
	public TravelNotFoundException() {
		super(ErrorCode.TRAVEL_NOT_FOUND);
	}
}
