package com.seoulchonnom.aggregate.schedule.exception;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InvalidScheduleRecurrenceException extends BadRequestException {
	public InvalidScheduleRecurrenceException() {
		super(ErrorCode.INVALID_SCHEDULE_RECURRENCE);
	}
}
