package com.seoulchonnom.spec.schedule.mapper;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleRdo;

class ScheduleMapperTest {
	private final ScheduleMapper scheduleMapper = new ScheduleMapper();

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
			.build();
		schedule.setId("schedule-1");

		ScheduleRdo scheduleRdo = scheduleMapper.toScheduleRdo(schedule);

		assertThat(scheduleRdo.getId()).isEqualTo("schedule-1");
		assertThat(scheduleRdo.getStart()).isEqualTo("2026-03-31T10:00:00+09:00");
		assertThat(scheduleRdo.getEnd()).isEqualTo("2026-03-31T11:00:00+09:00");
		assertThat(scheduleRdo.isAllDay()).isFalse();
		assertThat(scheduleRdo.getLocation()).isEqualTo("Seoul");
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
