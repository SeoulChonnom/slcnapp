package com.seoulchonnom.slcnapp.trip.exception;

import static com.seoulchonnom.slcnapp.trip.TripConstant.*;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

public class FilePathInvaildException extends BadRequestException {
	public FilePathInvaildException() {
		super(FILE_PATH_INVALID_ERROR_MESSAGE);
	}
}
