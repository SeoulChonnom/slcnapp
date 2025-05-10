package com.seoulchonnom.slcnapp.trip.exception;

import com.seoulchonnom.slcnapp.common.exception.UnsupportedMediaTypeException;

import static com.seoulchonnom.slcnapp.trip.TripConstant.TRIP_FILE_EXT_ERROR_MESSAGE;

public class TripFileExtException extends UnsupportedMediaTypeException {
    public TripFileExtException() {
        super(TRIP_FILE_EXT_ERROR_MESSAGE);
    }
}