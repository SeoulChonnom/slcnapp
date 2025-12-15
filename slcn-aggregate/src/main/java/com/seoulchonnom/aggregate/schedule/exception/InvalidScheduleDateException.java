package com.seoulchonnom.aggregate.schedule.exception;

import static com.seoulchonnom.spec.schedule.constant.ScheduleConstant.*;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;

public class InvalidScheduleDateException extends BadRequestException {
	public InvalidScheduleDateException() {
		super(INVALID_DATE_ERROR_MESSAGE);
	}
}
