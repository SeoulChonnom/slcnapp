package com.seoulchonnom.aggregate.trip.exception;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class TripNotFoundException extends BadRequestException {
	public TripNotFoundException() {
		super(ErrorCode.TRIP_NOT_FOUND);
	}
}
