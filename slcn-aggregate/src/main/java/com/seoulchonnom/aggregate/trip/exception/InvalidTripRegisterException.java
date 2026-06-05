package com.seoulchonnom.aggregate.trip.exception;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InvalidTripRegisterException extends BadRequestException {
	public InvalidTripRegisterException() {
		super(ErrorCode.INVALID_TRIP_REGISTER);
	}
}
