package com.seoulchonnom.aggregate.schedule.exception;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class ScheduleNotFoundException extends BadRequestException {
	public ScheduleNotFoundException() {
		super(ErrorCode.SCHEDULE_NOT_FOUND);
	}
}
