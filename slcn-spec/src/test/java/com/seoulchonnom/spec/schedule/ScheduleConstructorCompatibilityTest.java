package com.seoulchonnom.spec.schedule;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.schedule.entity.Schedule;

class ScheduleConstructorCompatibilityTest {
	@Test
	void constructor_withoutRecurrenceRule_shouldLeaveRecurrenceRuleNull() {
		Schedule schedule = new Schedule(
			"CALENDAR-0001",
			"저녁 약속",
			"성수동 식당 예약",
			false,
			LocalDateTime.of(2026, 9, 3, 19, 0),
			LocalDateTime.of(2026, 9, 3, 20, 0),
			"성수동");

		assertThat(schedule.getRecurrenceRule()).isNull();
		assertThat(schedule.getTitle()).isEqualTo("저녁 약속");
	}

	@Test
	void constructor_withRecurrenceRule_shouldKeepRecurrenceRule() {
		Schedule schedule = new Schedule(
			"CALENDAR-0001",
			"주간 회의",
			null,
			false,
			LocalDateTime.of(2026, 9, 3, 19, 0),
			LocalDateTime.of(2026, 9, 3, 20, 0),
			null,
			"FREQ=WEEKLY");

		assertThat(schedule.getRecurrenceRule()).isEqualTo("FREQ=WEEKLY");
	}
}
