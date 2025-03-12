package com.seoulchonnom.slcnapp.schedule.exception;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;
import com.seoulchonnom.slcnapp.schedule.ScheduleConstant;

public class InvalidScheduleDateException extends BadRequestException {
    public InvalidScheduleDateException() {
        super(ScheduleConstant.INVALID_DATE_ERROR_MESSAGE);
    }
}
