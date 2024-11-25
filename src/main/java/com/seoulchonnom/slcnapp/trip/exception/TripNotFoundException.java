package com.seoulchonnom.slcnapp.trip.exception;

import static com.seoulchonnom.slcnapp.trip.TripConstant.*;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

public class TripNotFoundException extends BadRequestException {
	public TripNotFoundException() {
		super(TRIP_NOT_FOUND_ERROR_MESSAGE);
	}
}
