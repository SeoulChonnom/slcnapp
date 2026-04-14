package com.seoulchonnom.aggregate.calendar.exception;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class CalendarNotFoundException extends BadRequestException {
	public CalendarNotFoundException() {
		super(ErrorCode.CALENDAR_NOT_FOUND);
	}
}
