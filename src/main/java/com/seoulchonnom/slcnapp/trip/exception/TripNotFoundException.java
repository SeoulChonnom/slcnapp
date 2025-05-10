package com.seoulchonnom.slcnapp.trip.exception;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

import static com.seoulchonnom.slcnapp.trip.TripConstant.TRIP_NOT_FOUND_ERROR_MESSAGE;

public class TripNotFoundException extends BadRequestException {
    public TripNotFoundException() {
        super(TRIP_NOT_FOUND_ERROR_MESSAGE);
    }
}
