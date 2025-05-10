package com.seoulchonnom.slcnapp.trip.exception;

import com.seoulchonnom.slcnapp.common.exception.PayloadTooLargeException;

import static com.seoulchonnom.slcnapp.trip.TripConstant.TRIP_FILE_SIZE_ERROR_MESSAGE;

public class TripFileSizeException extends PayloadTooLargeException {
	public TripFileSizeException() {
		super(TRIP_FILE_SIZE_ERROR_MESSAGE);
	}
}
