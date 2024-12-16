package com.seoulchonnom.slcnapp.trip.exception;

import static com.seoulchonnom.slcnapp.trip.TripConstant.*;

import com.seoulchonnom.slcnapp.common.exception.UnsupportedMediaTypeException;

public class TripFileExtException extends UnsupportedMediaTypeException {
	public TripFileExtException() {
		super(TRIP_FILE_EXT_ERROR_MESSAGE);
	}
}