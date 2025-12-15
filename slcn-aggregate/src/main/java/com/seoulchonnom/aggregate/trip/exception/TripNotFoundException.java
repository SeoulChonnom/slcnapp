package com.seoulchonnom.aggregate.trip.exception;

import static com.seoulchonnom.spec.trip.constant.TripConstant.*;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;

public class TripNotFoundException extends BadRequestException {
	public TripNotFoundException() {
		super(TRIP_NOT_FOUND_ERROR_MESSAGE);
	}
}
