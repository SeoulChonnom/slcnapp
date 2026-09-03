package com.seoulchonnom.spec.schedule.mapper;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleCdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleRdo;

class ScheduleMapperTest {
	private final ScheduleMapper scheduleMapper = Mappers.getMapper(ScheduleMapper.class);

	@Test
	void toScheduleRdo_shouldFormatDateTimeFields() {
			Schedule schedule = Schedule.builder()
			.calendarId("calendar-1")
			.title("Meeting")
			.body("Body")
			.allDay(false)
			.start(LocalDateTime.of(2026, 3, 31, 10, 0, 0))
			.end(LocalDateTime.of(2026, 3, 31, 11, 0, 0))
			.location("Seoul")
			.recurrenceRule("FREQ=DAILY;COUNT=3")
			.build();
		schedule.setId("schedule-1");

		ScheduleRdo scheduleRdo = scheduleMapper.toScheduleRdo(schedule);

		assertThat(scheduleRdo.getId()).isEqualTo("schedule-1");
		assertThat(scheduleRdo.getStart()).isEqualTo("2026-03-31T10:00:00+09:00");
		assertThat(scheduleRdo.getEnd()).isEqualTo("2026-03-31T11:00:00+09:00");
		assertThat(scheduleRdo.isAllDay()).isFalse();
		assertThat(scheduleRdo.getLocation()).isEqualTo("Seoul");
		assertThat(scheduleRdo.getRecurrenceRule()).isEqualTo("FREQ=DAILY;COUNT=3");
		assertThat(scheduleRdo.getOccurrenceId()).isNull();
	}

	@Test
	void toSchedule_shouldPreserveRecurrenceRuleFromCdo() {
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		scheduleCdo.setCalendarId("calendar-1");
		scheduleCdo.setTitle("Meeting");
		scheduleCdo.setStart("2026-03-31T10:00:00+09:00");
		scheduleCdo.setEnd("2026-03-31T11:00:00+09:00");
		scheduleCdo.setRecurrenceRule("FREQ=DAILY;COUNT=3");

		Schedule schedule = scheduleMapper.toSchedule(scheduleCdo);

		assertThat(schedule.getRecurrenceRule()).isEqualTo("FREQ=DAILY;COUNT=3");
	}

	@Test
	void toScheduleRdo_shouldLeaveRecurrenceFieldsNullForNonRecurringSchedule() {
		Schedule schedule = Schedule.builder()
			.title("One-time")
			.start(LocalDateTime.of(2026, 4, 1, 10, 0, 0))
			.end(LocalDateTime.of(2026, 4, 1, 11, 0, 0))
			.build();

		ScheduleRdo scheduleRdo = scheduleMapper.toScheduleRdo(schedule);

		assertThat(scheduleRdo.getRecurrenceRule()).isNull();
		assertThat(scheduleRdo.getOccurrenceId()).isNull();
	}

	@Test
	void toScheduleRdo_shouldFormatAllDayFieldsAsDateOnly() {
		Schedule schedule = Schedule.builder()
			.title("Holiday")
			.allDay(true)
			.start(LocalDateTime.of(2026, 4, 1, 0, 0, 0))
			.end(LocalDateTime.of(2026, 4, 2, 0, 0, 0))
			.build();

		ScheduleRdo scheduleRdo = scheduleMapper.toScheduleRdo(schedule);

		assertThat(scheduleRdo.getStart()).isEqualTo("2026-04-01");
		assertThat(scheduleRdo.getEnd()).isEqualTo("2026-04-02");
	}
}
