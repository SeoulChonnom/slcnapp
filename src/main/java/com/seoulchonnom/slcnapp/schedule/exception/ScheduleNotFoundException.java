package com.seoulchonnom.slcnapp.schedule.exception;

import com.seoulchonnom.slcnapp.common.exception.BadRequestException;
import com.seoulchonnom.slcnapp.schedule.ScheduleConstant;

public class ScheduleNotFoundException extends BadRequestException {
	public ScheduleNotFoundException() {
		super(ScheduleConstant.SCHEDULE_NOT_FOND_ERROR_MESSAGE);
	}
}
