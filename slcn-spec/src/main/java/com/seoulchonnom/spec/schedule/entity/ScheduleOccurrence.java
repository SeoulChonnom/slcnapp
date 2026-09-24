package com.seoulchonnom.spec.schedule.entity;

import java.time.LocalDateTime;

public record ScheduleOccurrence(
	Schedule schedule,
	LocalDateTime start,
	LocalDateTime end,
	String occurrenceId
) {
}
