package com.seoulchonnom.slcnapp.schedule.exception;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

import static com.seoulchonnom.slcnapp.schedule.ScheduleConstant.INVALID_DATE_ERROR_MESSAGE;

public class InvalidScheduleRegisterRequestException extends BadRequestException {
    public InvalidScheduleRegisterRequestException() {
        super(INVALID_DATE_ERROR_MESSAGE);
    }
}
