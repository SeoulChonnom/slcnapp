package com.seoulchonnom.spec.schedule.feed.entity;

import com.seoulchonnom.spec.calendar.entity.Calendar;
import com.seoulchonnom.spec.schedule.entity.Schedule;

public record ScheduleFeedEvent(Schedule schedule, Calendar calendar) {
}
