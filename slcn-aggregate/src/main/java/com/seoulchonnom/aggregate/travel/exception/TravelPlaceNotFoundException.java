package com.seoulchonnom.aggregate.travel.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class TravelPlaceNotFoundException extends BusinessException {
	public TravelPlaceNotFoundException() {
		super(ErrorCode.TRAVEL_PLACE_NOT_FOUND);
	}
}
