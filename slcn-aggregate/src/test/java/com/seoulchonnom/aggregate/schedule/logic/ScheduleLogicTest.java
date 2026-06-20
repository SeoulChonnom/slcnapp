package com.seoulchonnom.aggregate.schedule.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seoulchonnom.aggregate.calendar.store.CalendarStore;
import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.schedule.exception.InvalidScheduleDateException;
import com.seoulchonnom.aggregate.schedule.exception.InvalidScheduleRegisterRequestException;
import com.seoulchonnom.aggregate.schedule.store.ScheduleStore;
import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleCdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleRdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleSearchSdo;
import com.seoulchonnom.spec.schedule.mapper.ScheduleMapper;

class ScheduleLogicTest {
	private final CalendarStore calendarStore = mock(CalendarStore.class);
	private final ScheduleStore scheduleStore = mock(ScheduleStore.class);
	private final ScheduleMapper scheduleMapper = mock(ScheduleMapper.class);
	private final ScheduleLogic scheduleLogic = new ScheduleLogic(calendarStore, scheduleStore, scheduleMapper);

	@Test
	void getSchedules_shouldUseRangeQueryWhenStartAndEndProvided() {
		ScheduleSearchSdo searchSdo = new ScheduleSearchSdo("2026-04-01T00:00:00+09:00", "2026-04-08T00:00:00+09:00");
		Schedule schedule = Schedule.builder()
			.start(LocalDateTime.of(2026, 4, 2, 12, 0))
			.end(LocalDateTime.of(2026, 4, 2, 13, 0))
			.build();
		ScheduleRdo scheduleRdo = new ScheduleRdo();
		when(scheduleStore.findAllByDateRange(
			LocalDateTime.of(2026, 4, 1, 0, 0),
			LocalDateTime.of(2026, 4, 8, 0, 0))).thenReturn(List.of(schedule));
		when(scheduleMapper.toScheduleRdo(schedule)).thenReturn(scheduleRdo);

		List<ScheduleRdo> result = scheduleLogic.getSchedules(searchSdo);

		assertThat(result).containsExactly(scheduleRdo);
	}

	@Test
	void getSchedules_shouldRejectMissingRangeStart() {
		ScheduleSearchSdo searchSdo = new ScheduleSearchSdo(null, "2026-04-08T00:00:00+09:00");

		assertThatThrownBy(() -> scheduleLogic.getSchedules(searchSdo))
			.isInstanceOf(InvalidScheduleDateException.class);
	}

	@Test
	void getSchedules_shouldRejectMissingRangeEnd() {
		ScheduleSearchSdo searchSdo = new ScheduleSearchSdo("2026-04-01T00:00:00+09:00", null);

		assertThatThrownBy(() -> scheduleLogic.getSchedules(searchSdo))
			.isInstanceOf(InvalidScheduleDateException.class);
	}

	@Test
	void getSchedulesForRange_shouldRejectInvalidIsoDateTime() {
		assertThatThrownBy(() -> scheduleLogic.getSchedulesForRange("2026-04-01 00:00:00", "2026-04-08T00:00:00+09:00"))
			.isInstanceOf(InvalidScheduleDateException.class);
	}

	@Test
	void getSchedulesForRange_shouldRejectRangeLongerThanOneMonth() {
		assertThatThrownBy(() -> scheduleLogic.getSchedulesForRange("2026-04-01T00:00:00+09:00", "2026-05-02T00:00:00+09:00"))
			.isInstanceOf(InvalidScheduleDateException.class);
	}

	@Test
	void registerSchedule_shouldRejectBlankTitle() {
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		scheduleCdo.setCalendarId("cal1");
		scheduleCdo.setTitle(" ");
		scheduleCdo.setStart("2026-04-01T09:00:00+09:00");
		scheduleCdo.setEnd("2026-04-01T10:00:00+09:00");
		when(calendarStore.existsVisibleById("cal1")).thenReturn(true);

		assertThatThrownBy(() -> scheduleLogic.registerSchedule(scheduleCdo))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("title은 필수입니다.");
	}

	@Test
	void registerSchedule_shouldRejectLegacyDateFormat() {
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		scheduleCdo.setCalendarId("cal1");
		scheduleCdo.setTitle("Meeting");
		scheduleCdo.setStart("2026-04-01 09:00:00");
		scheduleCdo.setEnd("2026-04-01 10:00:00");
		when(calendarStore.existsVisibleById("cal1")).thenReturn(true);

		assertThatThrownBy(() -> scheduleLogic.registerSchedule(scheduleCdo))
			.isInstanceOf(InvalidScheduleRegisterRequestException.class);
	}

	@Test
	void registerSchedule_shouldRejectHiddenOrMissingCalendar() {
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		scheduleCdo.setCalendarId("cal1");
		scheduleCdo.setTitle("Meeting");
		scheduleCdo.setStart("2026-04-01T09:00:00+09:00");
		scheduleCdo.setEnd("2026-04-01T10:00:00+09:00");
		when(calendarStore.existsVisibleById("cal1")).thenReturn(false);

		assertThatThrownBy(() -> scheduleLogic.registerSchedule(scheduleCdo))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("사용할 수 없는 calendarId입니다.");
	}

	@Test
	void registerSchedule_shouldSaveAllDaySchedule() {
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		scheduleCdo.setCalendarId("cal1");
		scheduleCdo.setTitle("Holiday");
		scheduleCdo.setAllDay(true);
		scheduleCdo.setStart("2026-04-01");
		scheduleCdo.setEnd("2026-04-02");
		scheduleCdo.setLocation("Seoul");
		when(calendarStore.existsVisibleById("cal1")).thenReturn(true);
		ScheduleRdo scheduleRdo = new ScheduleRdo();
		when(scheduleMapper.toScheduleRdo(any(Schedule.class))).thenReturn(scheduleRdo);

		scheduleLogic.registerSchedule(scheduleCdo);

		ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
		verify(scheduleStore).save(scheduleCaptor.capture());
		assertThat(scheduleCaptor.getValue().isAllDay()).isTrue();
		assertThat(scheduleCaptor.getValue().getStart()).isEqualTo(LocalDateTime.of(2026, 4, 1, 0, 0));
		assertThat(scheduleCaptor.getValue().getEnd()).isEqualTo(LocalDateTime.of(2026, 4, 2, 0, 0));
		assertThat(scheduleCaptor.getValue().getLocation()).isEqualTo("Seoul");
		assertThat(scheduleCaptor.getValue().isHidden()).isFalse();
	}

	@Test
	void hideSchedule_shouldMarkScheduleAsHidden() {
		Schedule schedule = Schedule.builder()
			.calendarId("cal1")
			.title("Hidden")
			.hidden(false)
			.build();
		when(scheduleStore.findById("schedule-1")).thenReturn(schedule);

		scheduleLogic.hideSchedule("schedule-1");

		assertThat(schedule.isHidden()).isTrue();
		verify(scheduleStore).save(schedule);
	}
}
