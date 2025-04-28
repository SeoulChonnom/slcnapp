package com.seoulchonnom.slcnapp.schedule.exception;

import static com.seoulchonnom.slcnapp.schedule.ScheduleConstant.*;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;

public class InvalidScheduleRegisterRequestException extends BadRequestException {
	public InvalidScheduleRegisterRequestException() {
		super(INVALID_DATE_ERROR_MESSAGE);
	}
}
