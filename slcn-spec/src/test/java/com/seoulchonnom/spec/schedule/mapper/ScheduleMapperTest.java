package com.seoulchonnom.spec.schedule.mapper;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.entity.ScheduleCategory;
import com.seoulchonnom.spec.schedule.entity.ScheduleState;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleRdo;

class ScheduleMapperTest {
	private final ScheduleMapper scheduleMapper = new ScheduleMapper();

	@Test
	void toScheduleRdo_shouldFormatDateTimeFields() {
		Schedule schedule = Schedule.builder()
			.calendarId("calendar-1")
			.title("Meeting")
			.body("Body")
			.isAllDay(false)
			.start(LocalDateTime.of(2026, 3, 31, 10, 0, 0))
			.end(LocalDateTime.of(2026, 3, 31, 11, 0, 0))
			.goingDuration(10L)
			.comingDuration(20L)
			.location("Seoul")
			.category(ScheduleCategory.time)
			.dueDateClass("class-a")
			.recurrenceRule("FREQ=DAILY")
			.state(ScheduleState.Busy)
			.isVisible(true)
			.isPending(false)
			.isFocused(true)
			.isReadOnly(false)
			.isPrivate(true)
			.color("#111111")
			.backgroundColor("#222222")
			.dragBackgroundColor("#333333")
			.borderColor("#444444")
			.customStyle("{}")
			.build();
		schedule.setId("schedule-1");

		ScheduleRdo scheduleRdo = scheduleMapper.toScheduleRdo(schedule);

		assertThat(scheduleRdo.getId()).isEqualTo("schedule-1");
		assertThat(scheduleRdo.getStart()).isEqualTo("2026-03-31 10:00:00");
		assertThat(scheduleRdo.getEnd()).isEqualTo("2026-03-31 11:00:00");
		assertThat(scheduleRdo.isVisible()).isTrue();
		assertThat(scheduleRdo.isPrivate()).isTrue();
	}
}
