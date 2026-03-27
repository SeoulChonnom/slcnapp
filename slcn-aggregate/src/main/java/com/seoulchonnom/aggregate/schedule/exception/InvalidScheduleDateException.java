package com.seoulchonnom.aggregate.schedule.exception;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InvalidScheduleDateException extends BadRequestException {
	public InvalidScheduleDateException() {
		super(ErrorCode.INVALID_SCHEDULE_DATE);
	}
}
