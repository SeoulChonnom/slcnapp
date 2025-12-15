package com.seoulchonnom.aggregate.schedule.exception;

import static com.seoulchonnom.spec.schedule.constant.ScheduleConstant.*;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;

public class ScheduleNotFoundException extends BadRequestException {
	public ScheduleNotFoundException() {
		super(SCHEDULE_NOT_FOND_ERROR_MESSAGE);
	}
}
