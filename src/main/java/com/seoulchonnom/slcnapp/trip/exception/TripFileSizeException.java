package com.seoulchonnom.slcnapp.trip.exception;

import static com.seoulchonnom.slcnapp.trip.TripConstant.*;

import com.seoulchonnom.slcnapp.common.exception.PayloadTooLargeException;

public class TripFileSizeException extends PayloadTooLargeException {
	public TripFileSizeException() {
		super(TRIP_FILE_SIZE_ERROR_MESSAGE);
	}
}
