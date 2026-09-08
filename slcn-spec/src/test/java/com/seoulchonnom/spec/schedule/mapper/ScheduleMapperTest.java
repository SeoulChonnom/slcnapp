package com.seoulchonnom.spec.schedule.mapper;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.entity.ScheduleOccurrence;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleCdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleRdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleUdo;

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

	@Test
	void toScheduleRdo_shouldMapOccurrenceDatesWithoutMutatingMasterSchedule() {
		Schedule schedule = Schedule.builder()
			.calendarId("calendar-1")
			.title("Recurring meeting")
			.body("Body")
			.allDay(false)
			.start(LocalDateTime.of(2026, 1, 6, 19, 0))
			.end(LocalDateTime.of(2026, 1, 6, 20, 0))
			.location("Seoul")
			.recurrenceRule("FREQ=WEEKLY;BYDAY=TU")
			.build();
		schedule.setId("schedule-1");
		ScheduleOccurrence occurrence = new ScheduleOccurrence(
			schedule,
			LocalDateTime.of(2026, 9, 1, 19, 0),
			LocalDateTime.of(2026, 9, 1, 20, 0),
			"schedule-1/2026-09-01T19:00:00+09:00");

		ScheduleRdo scheduleRdo = scheduleMapper.toScheduleRdo(occurrence);

		assertThat(scheduleRdo.getId()).isEqualTo("schedule-1");
		assertThat(scheduleRdo.getCalendarId()).isEqualTo("calendar-1");
		assertThat(scheduleRdo.getTitle()).isEqualTo("Recurring meeting");
		assertThat(scheduleRdo.getStart()).isEqualTo("2026-09-01T19:00:00+09:00");
		assertThat(scheduleRdo.getEnd()).isEqualTo("2026-09-01T20:00:00+09:00");
		assertThat(scheduleRdo.getRecurrenceRule()).isEqualTo("FREQ=WEEKLY;BYDAY=TU");
		assertThat(scheduleRdo.getOccurrenceId()).isEqualTo("schedule-1/2026-09-01T19:00:00+09:00");
		assertThat(schedule.getStart()).isEqualTo(LocalDateTime.of(2026, 1, 6, 19, 0));
		assertThat(schedule.getEnd()).isEqualTo(LocalDateTime.of(2026, 1, 6, 20, 0));
	}

	@Test
	void toSchedule_whenStartEqualsEnd_shouldThrowIllegalArgumentException() {
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		scheduleCdo.setCalendarId("CALENDAR-0001");
		scheduleCdo.setTitle("저녁 약속");
		scheduleCdo.setAllDay(false);
		scheduleCdo.setStart("2026-09-03T19:00:00+09:00");
		scheduleCdo.setEnd("2026-09-03T19:00:00+09:00");

		assertThatThrownBy(() -> scheduleMapper.toSchedule(scheduleCdo))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("start는 end보다 빨라야 합니다.");
	}

	@Test
	void toSchedule_whenStartAfterEnd_shouldThrowIllegalArgumentException() {
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		scheduleCdo.setCalendarId("CALENDAR-0001");
		scheduleCdo.setTitle("저녁 약속");
		scheduleCdo.setAllDay(false);
		scheduleCdo.setStart("2026-09-03T20:00:00+09:00");
		scheduleCdo.setEnd("2026-09-03T19:00:00+09:00");

		assertThatThrownBy(() -> scheduleMapper.toSchedule(scheduleCdo))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void toSchedule_whenAllDayEndIsExclusiveNextDay_shouldSucceed() {
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		scheduleCdo.setCalendarId("CALENDAR-0001");
		scheduleCdo.setTitle("휴가");
		scheduleCdo.setAllDay(true);
		scheduleCdo.setStart("2026-09-03");
		scheduleCdo.setEnd("2026-09-04");

		Schedule schedule = scheduleMapper.toSchedule(scheduleCdo);

		assertThat(schedule.getStart()).isEqualTo(LocalDateTime.of(2026, 9, 3, 0, 0));
		assertThat(schedule.getEnd()).isEqualTo(LocalDateTime.of(2026, 9, 4, 0, 0));
	}

	@Test
	void toSchedule_whenAllDayEndIsSameDay_shouldThrowIllegalArgumentException() {
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		scheduleCdo.setCalendarId("CALENDAR-0001");
		scheduleCdo.setTitle("휴가");
		scheduleCdo.setAllDay(true);
		scheduleCdo.setStart("2026-09-03");
		scheduleCdo.setEnd("2026-09-03");

		assertThatThrownBy(() -> scheduleMapper.toSchedule(scheduleCdo))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void updateSchedule_whenStartEqualsEnd_shouldThrowIllegalArgumentException() {
		Schedule schedule = new Schedule(
			"CALENDAR-0001", "저녁 약속", null, false,
			LocalDateTime.of(2026, 9, 3, 19, 0),
			LocalDateTime.of(2026, 9, 3, 20, 0), null);

		ScheduleUdo scheduleUdo = new ScheduleUdo();
		scheduleUdo.setId("SCHEDULE-0001");
		scheduleUdo.setCalendarId("CALENDAR-0001");
		scheduleUdo.setTitle("저녁 약속");
		scheduleUdo.setAllDay(false);
		scheduleUdo.setStart("2026-09-03T19:00:00+09:00");
		scheduleUdo.setEnd("2026-09-03T19:00:00+09:00");

		assertThatThrownBy(() -> scheduleMapper.updateSchedule(scheduleUdo, schedule))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
