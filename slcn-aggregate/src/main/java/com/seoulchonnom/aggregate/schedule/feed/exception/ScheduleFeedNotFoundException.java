package com.seoulchonnom.aggregate.schedule.feed.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class ScheduleFeedNotFoundException extends BusinessException {
	public ScheduleFeedNotFoundException() {
		super(ErrorCode.SCHEDULE_FEED_NOT_FOUND);
	}
}
