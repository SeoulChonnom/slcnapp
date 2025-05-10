package com.seoulchonnom.slcnapp.trip.exception;

import com.seoulchonnom.slcnapp.common.exception.InternalServerErrorException;

import static com.seoulchonnom.slcnapp.trip.TripConstant.TRIP_FILE_UPLOAD_ERROR_MESSAGE;

public class TripFileUploadException extends InternalServerErrorException {
	public TripFileUploadException() {
		super(TRIP_FILE_UPLOAD_ERROR_MESSAGE);
	}
}
