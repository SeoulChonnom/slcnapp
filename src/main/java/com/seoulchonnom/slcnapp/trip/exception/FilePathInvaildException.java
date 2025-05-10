package com.seoulchonnom.slcnapp.trip.exception;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

import static com.seoulchonnom.slcnapp.trip.TripConstant.FILE_PATH_INVALID_ERROR_MESSAGE;

public class FilePathInvaildException extends BadRequestException {
	public FilePathInvaildException() {
		super(FILE_PATH_INVALID_ERROR_MESSAGE);
	}
}
