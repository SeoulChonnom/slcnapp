package com.seoulchonnom.spec.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleCdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleUdo;

class ScheduleConstructorCompatibilityTest {
	@Test
	void schedule_shouldKeepThePreRecurrencePositionalConstructor() {
		Schedule schedule = new Schedule(
			"calendar-1",
			"Title",
			"Body",
			false,
			LocalDateTime.of(2026, 9, 1, 9, 0),
			LocalDateTime.of(2026, 9, 1, 10, 0),
			"Seoul",
			false);

		assertThat(schedule.getRecurrenceRule()).isNull();
	}

	@Test
	void scheduleCdo_shouldKeepThePreRecurrencePositionalConstructor() {
		ScheduleCdo scheduleCdo = new ScheduleCdo(
			"calendar-1",
			"Title",
			"Body",
			"2026-09-01T09:00:00+09:00",
			"2026-09-01T10:00:00+09:00",
			false,
			"Seoul");

		assertThat(scheduleCdo.getRecurrenceRule()).isNull();
	}

	@Test
	void scheduleUdo_shouldKeepThePreRecurrencePositionalConstructor() {
		ScheduleUdo scheduleUdo = new ScheduleUdo(
			"schedule-1",
			"calendar-1",
			"Title",
			"Body",
			"2026-09-01T09:00:00+09:00",
			"2026-09-01T10:00:00+09:00",
			false,
			"Seoul");

		assertThat(scheduleUdo.getRecurrenceRule()).isNull();
	}
}
