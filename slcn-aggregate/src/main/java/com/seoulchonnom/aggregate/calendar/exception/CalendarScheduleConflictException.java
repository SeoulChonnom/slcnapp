package com.seoulchonnom.aggregate.calendar.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class CalendarScheduleConflictException extends BusinessException {
	public CalendarScheduleConflictException() {
		super(ErrorCode.CALENDAR_SCHEDULE_CONFLICT);
	}

	public CalendarScheduleConflictException(String message) {
		super(ErrorCode.CALENDAR_SCHEDULE_CONFLICT, message);
	}
}
