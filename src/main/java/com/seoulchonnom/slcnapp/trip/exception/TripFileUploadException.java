package com.seoulchonnom.slcnapp.trip.exception;

import static com.seoulchonnom.slcnapp.trip.TripConstant.*;

import com.seoulchonnom.slcnapp.common.exception.InternalServerErrorException;

public class TripFileUploadException extends InternalServerErrorException {
	public TripFileUploadException() {
		super(TRIP_FILE_UPLOAD_ERROR_MESSAGE);
	}
}
