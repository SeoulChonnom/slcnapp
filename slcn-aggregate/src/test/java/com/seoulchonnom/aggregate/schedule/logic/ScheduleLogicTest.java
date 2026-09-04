package com.seoulchonnom.aggregate.schedule.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
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
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleUdo;
import com.seoulchonnom.spec.schedule.mapper.ScheduleMapper;

class ScheduleLogicTest {
	private final CalendarStore calendarStore = mock(CalendarStore.class);
	private final ScheduleStore scheduleStore = mock(ScheduleStore.class);
	private final ScheduleMapper scheduleMapper = spy(Mappers.getMapper(ScheduleMapper.class));
	private final ScheduleRecurrenceRuleValidator recurrenceRuleValidator =
		spy(new ScheduleRecurrenceRuleValidator());
	private final ScheduleRecurrenceExpander recurrenceExpander =
		new ScheduleRecurrenceExpander(recurrenceRuleValidator);
	private final ScheduleLogic scheduleLogic = new ScheduleLogic(
		calendarStore,
		scheduleStore,
		scheduleMapper,
		recurrenceRuleValidator,
		recurrenceExpander);

	@Test
	void getSchedules_shouldExpandOldRecurrenceMasterAndSortByStartThenOccurrenceId() {
		ScheduleSearchSdo searchSdo = new ScheduleSearchSdo(
			"2026-09-01T00:00:00+09:00",
			"2026-10-01T00:00:00+09:00");
		Schedule oneTime = Schedule.builder()
			.title("One-time")
			.start(LocalDateTime.of(2026, 9, 1, 19, 0))
			.end(LocalDateTime.of(2026, 9, 1, 20, 0))
			.build();
		oneTime.setId("schedule-1");
		Schedule oldRecurrenceMaster = Schedule.builder()
			.title("Weekly")
			.start(LocalDateTime.of(2026, 1, 6, 19, 0))
			.end(LocalDateTime.of(2026, 1, 6, 20, 0))
			.recurrenceRule("FREQ=WEEKLY;BYDAY=TU")
			.build();
		oldRecurrenceMaster.setId("schedule-2");
		LocalDateTime rangeStart = LocalDateTime.of(2026, 9, 1, 0, 0);
		LocalDateTime rangeEnd = LocalDateTime.of(2026, 10, 1, 0, 0);
		when(scheduleStore.findCandidatesByDateRange(rangeStart, rangeEnd))
			.thenReturn(List.of(oldRecurrenceMaster, oneTime));

		List<ScheduleRdo> result = scheduleLogic.getSchedules(searchSdo);

		assertThat(result).extracting(ScheduleRdo::getStart)
			.containsExactly(
				"2026-09-01T19:00:00+09:00",
				"2026-09-01T19:00:00+09:00",
				"2026-09-08T19:00:00+09:00",
				"2026-09-15T19:00:00+09:00",
				"2026-09-22T19:00:00+09:00",
				"2026-09-29T19:00:00+09:00");
		assertThat(result).extracting(ScheduleRdo::getId)
			.containsExactly("schedule-1", "schedule-2", "schedule-2", "schedule-2", "schedule-2", "schedule-2");
		assertThat(result).extracting(ScheduleRdo::getOccurrenceId)
			.containsExactly(
				(String)null,
				"schedule-2/2026-09-01T19:00:00+09:00",
				"schedule-2/2026-09-08T19:00:00+09:00",
				"schedule-2/2026-09-15T19:00:00+09:00",
				"schedule-2/2026-09-22T19:00:00+09:00",
				"schedule-2/2026-09-29T19:00:00+09:00");
		verify(scheduleStore).findCandidatesByDateRange(rangeStart, rangeEnd);
		verify(scheduleStore, never()).findAllByDateRange(any(), any());
	}

	@Test
	void getSchedulesForMonth_shouldUseCandidatesAndKeepNonRecurringScheduleUnchanged() {
		LocalDateTime rangeStart = LocalDateTime.of(2026, 9, 1, 0, 0);
		LocalDateTime rangeEnd = LocalDateTime.of(2026, 10, 1, 0, 0);
		Schedule schedule = Schedule.builder()
			.title("One-time")
			.start(LocalDateTime.of(2026, 9, 10, 9, 0))
			.end(LocalDateTime.of(2026, 9, 10, 10, 0))
			.build();
		schedule.setId("schedule-1");
		when(scheduleStore.findCandidatesByDateRange(rangeStart, rangeEnd)).thenReturn(List.of(schedule));

		List<ScheduleRdo> result = scheduleLogic.getSchedulesForMonth(2026, 9);

		assertThat(result).singleElement()
			.satisfies(scheduleRdo -> {
				assertThat(scheduleRdo.getId()).isEqualTo("schedule-1");
				assertThat(scheduleRdo.getStart()).isEqualTo("2026-09-10T09:00:00+09:00");
				assertThat(scheduleRdo.getEnd()).isEqualTo("2026-09-10T10:00:00+09:00");
				assertThat(scheduleRdo.getOccurrenceId()).isNull();
			});
		verify(scheduleStore).findCandidatesByDateRange(rangeStart, rangeEnd);
		verify(scheduleStore, never()).findAllByDateRange(any(), any());
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
	void registerSchedule_shouldRejectEqualStartAndEnd() {
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		scheduleCdo.setCalendarId("cal1");
		scheduleCdo.setTitle("Zero duration");
		scheduleCdo.setStart("2026-04-01T09:00:00+09:00");
		scheduleCdo.setEnd("2026-04-01T09:00:00+09:00");
		when(calendarStore.existsVisibleById("cal1")).thenReturn(true);

		assertThatThrownBy(() -> scheduleLogic.registerSchedule(scheduleCdo))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("start는 end보다 빨라야 합니다.");
		verify(scheduleStore, never()).save(any(Schedule.class));
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
		doReturn(scheduleRdo).when(scheduleMapper).toScheduleRdo(any(Schedule.class));

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
	void registerSchedule_shouldValidateAndNormalizeRecurrenceRule() {
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		scheduleCdo.setCalendarId("cal1");
		scheduleCdo.setTitle("Meeting");
		scheduleCdo.setStart("2026-04-01T09:00:00+09:00");
		scheduleCdo.setEnd("2026-04-01T10:00:00+09:00");
		scheduleCdo.setRecurrenceRule(" ");
		when(calendarStore.existsVisibleById("cal1")).thenReturn(true);

		scheduleLogic.registerSchedule(scheduleCdo);

		verify(recurrenceRuleValidator).validateAndNormalize(scheduleCdo.getRecurrenceRule(), scheduleCdo.isAllDay());
		ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
		verify(scheduleStore).save(scheduleCaptor.capture());
		assertThat(scheduleCaptor.getValue().getRecurrenceRule()).isNull();
	}

	@Test
	void registerSchedule_shouldPreserveValidRecurrenceRuleByteForByte() {
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		scheduleCdo.setCalendarId("cal1");
		scheduleCdo.setTitle("Meeting");
		scheduleCdo.setStart("2026-04-01T09:00:00+09:00");
		scheduleCdo.setEnd("2026-04-01T10:00:00+09:00");
		String registeredRule = "BYDAY=MO,WE;FREQ=WEEKLY;COUNT=3";
		scheduleCdo.setRecurrenceRule(registeredRule);
		when(calendarStore.existsVisibleById("cal1")).thenReturn(true);

		scheduleLogic.registerSchedule(scheduleCdo);

		verify(recurrenceRuleValidator).validateAndNormalize(registeredRule, scheduleCdo.isAllDay());
		ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
		verify(scheduleStore).save(scheduleCaptor.capture());
		assertThat(scheduleCaptor.getValue().getRecurrenceRule()).isEqualTo(registeredRule);
	}

	@Test
	void modifySchedule_shouldValidateRecurrencePreserveItAndTouchModifiedTime() {
		ScheduleUdo scheduleUdo = new ScheduleUdo();
		scheduleUdo.setId("schedule-1");
		scheduleUdo.setCalendarId("cal1");
		scheduleUdo.setTitle("Updated");
		scheduleUdo.setStart("2026-04-01T09:00:00+09:00");
		scheduleUdo.setEnd("2026-04-01T10:00:00+09:00");
		String modifiedRule = "BYDAY=MO,WE;FREQ=WEEKLY;COUNT=3";
		scheduleUdo.setRecurrenceRule(modifiedRule);
		when(calendarStore.existsVisibleById("cal1")).thenReturn(true);
		Schedule schedule = Schedule.builder()
			.calendarId("cal1")
			.title("Original")
			.start(LocalDateTime.of(2026, 3, 30, 9, 0))
			.end(LocalDateTime.of(2026, 3, 30, 10, 0))
			.build();
		schedule.setId("schedule-1");
		schedule.setModifiedTime(1L);
		when(scheduleStore.findById("schedule-1")).thenReturn(schedule);

		scheduleLogic.modifySchedule(scheduleUdo);

		verify(recurrenceRuleValidator).validateAndNormalize(modifiedRule, scheduleUdo.isAllDay());
		assertThat(schedule.getRecurrenceRule()).isEqualTo(modifiedRule);
		assertThat(schedule.getModifiedTime()).isGreaterThan(1L);
		verify(scheduleStore).save(schedule);
	}

	@Test
	void hideSchedule_shouldMarkScheduleAsHiddenAndTouchModifiedTime() {
		Schedule schedule = Schedule.builder()
			.calendarId("cal1")
			.title("Hidden")
			.hidden(false)
			.build();
		schedule.setModifiedTime(1L);
		when(scheduleStore.findById("schedule-1")).thenReturn(schedule);

		scheduleLogic.hideSchedule("schedule-1");

		assertThat(schedule.isHidden()).isTrue();
		assertThat(schedule.getModifiedTime()).isGreaterThan(1L);
		verify(scheduleStore).save(schedule);
	}
}
