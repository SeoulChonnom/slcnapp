package com.seoulchonnom.aggregate.calendar.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import org.springframework.dao.DataIntegrityViolationException;

import com.seoulchonnom.aggregate.calendar.exception.CalendarScheduleConflictException;
import com.seoulchonnom.aggregate.calendar.store.CalendarStore;
import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.schedule.store.ScheduleStore;
import com.seoulchonnom.spec.calendar.entity.Calendar;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarCdo;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarRdo;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarUdo;
import com.seoulchonnom.spec.calendar.mapper.CalendarMapper;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.common.exception.ErrorCode;

class CalendarLogicTest {
	private final CalendarStore calendarStore = mock(CalendarStore.class);
	private final ScheduleStore scheduleStore = mock(ScheduleStore.class);
	private final IdGenerator idGenerator = mock(IdGenerator.class);
	private final CalendarMapper calendarMapper = spy(Mappers.getMapper(CalendarMapper.class));
	private final CalendarLogic calendarLogic = new CalendarLogic(calendarStore, scheduleStore, idGenerator, calendarMapper);

	@Test
	void getCalendars_shouldReturnVisibleCalendars() {
		Calendar calendar = Calendar.builder().name("아영").visible(true).sortOrder(1).build();
		CalendarRdo calendarRdo = new CalendarRdo();
		when(calendarStore.findAllVisible()).thenReturn(List.of(calendar));
		doReturn(calendarRdo).when(calendarMapper).toCalendarRdo(calendar);

		List<CalendarRdo> result = calendarLogic.getCalendars();

		assertThat(result).containsExactly(calendarRdo);
	}

	@Test
	void registerCalendar_shouldGenerateDomainIdAndSave() {
		CalendarCdo calendarCdo = new CalendarCdo("아영", "#FE9FC8", "#FE9FC8", "#111111", true, true, true, true, 1);
		CalendarRdo calendarRdo = new CalendarRdo();
		when(idGenerator.nextDomainId("CALENDAR")).thenReturn("CALENDAR-0001");
		doReturn(calendarRdo).when(calendarMapper).toCalendarRdo(any(Calendar.class));

		calendarLogic.registerCalendar(calendarCdo);

		ArgumentCaptor<Calendar> calendarCaptor = ArgumentCaptor.forClass(Calendar.class);
		verify(calendarStore).save(calendarCaptor.capture());
		assertThat(calendarCaptor.getValue().getId()).isEqualTo("CALENDAR-0001");
		assertThat(calendarCaptor.getValue().isVisible()).isTrue();
		assertThat(calendarCaptor.getValue().getName()).isEqualTo("아영");
	}

	@Test
	void registerCalendar_shouldRejectInvalidColor() {
		CalendarCdo calendarCdo = new CalendarCdo("아영", "pink", "#FE9FC8", "#111111", true, true, true, true, 1);

		assertThatThrownBy(() -> calendarLogic.registerCalendar(calendarCdo))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("backgroundColor는 #RRGGBB 형식이어야 합니다.");
	}

	@Test
	void registerCalendar_shouldRejectInvalidSortOrder() {
		CalendarCdo calendarCdo = new CalendarCdo("아영", "#FE9FC8", "#FE9FC8", "#111111", true, true, true, true, 0);

		assertThatThrownBy(() -> calendarLogic.registerCalendar(calendarCdo))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("sortOrder는 1 이상이어야 합니다.");
	}

	@Test
	void modifyCalendar_shouldUpdateExistingCalendar() {
		Calendar calendar = Calendar.builder()
			.name("기존")
			.backgroundColor("#000000")
			.borderColor("#000000")
			.textColor("#FFFFFF")
			.visible(true)
			.editable(false)
			.startEditable(false)
			.durationEditable(false)
			.defaultSelected(false)
			.sortOrder(5)
			.build();
		calendar.setId("CALENDAR-0001");
		CalendarUdo calendarUdo = new CalendarUdo("CALENDAR-0001", "아영", "#FE9FC8", "#FE9FC8", "#111111", true, true,
			true, true, 1);
		when(calendarStore.findById("CALENDAR-0001")).thenReturn(calendar);

		calendarLogic.modifyCalendar(calendarUdo);

		assertThat(calendar.getName()).isEqualTo("아영");
		assertThat(calendar.getSortOrder()).isEqualTo(1);
		verify(calendarStore).save(calendar);
	}

	@Test
	void modifyCalendar_shouldTouchModifiedTime() {
		Calendar calendar = Calendar.builder()
			.name("기존")
			.backgroundColor("#000000")
			.borderColor("#000000")
			.textColor("#FFFFFF")
			.visible(true)
			.sortOrder(5)
			.build();
		calendar.setId("CALENDAR-0001");
		calendar.setModifiedTime(1L);
		CalendarUdo calendarUdo = new CalendarUdo("CALENDAR-0001", "아영", "#FE9FC8", "#FE9FC8", "#111111", true,
			true, true, true, 1);
		when(calendarStore.findById("CALENDAR-0001")).thenReturn(calendar);

		calendarLogic.modifyCalendar(calendarUdo);

		assertThat(calendar.getModifiedTime()).isGreaterThan(1L);
	}

	@Test
	void hideCalendar_shouldMarkCalendarAsInvisible() {
		Calendar calendar = Calendar.builder().visible(true).build();
		when(calendarStore.findById("CALENDAR-0001")).thenReturn(calendar);

		calendarLogic.hideCalendar("CALENDAR-0001");

		assertThat(calendar.isVisible()).isFalse();
		verify(calendarStore).save(calendar);
	}

	@Test
	void deleteCalendar_shouldDeleteCalendar() {
		Calendar calendar = Calendar.builder().build();
		when(calendarStore.findById("CALENDAR-0001")).thenReturn(calendar);
		when(scheduleStore.existsByCalendarId("CALENDAR-0001")).thenReturn(false);

		calendarLogic.deleteCalendar("CALENDAR-0001");

		verify(calendarStore).delete(calendar);
	}

	@Test
	void deleteCalendar_shouldRejectCalendarReferencedBySchedule() {
		Calendar calendar = Calendar.builder().build();
		when(calendarStore.findById("CALENDAR-0001")).thenReturn(calendar);
		when(scheduleStore.existsByCalendarId("CALENDAR-0001")).thenReturn(true);

		Throwable thrown = catchThrowable(() -> calendarLogic.deleteCalendar("CALENDAR-0001"));
		assertThat(thrown)
			.isInstanceOf(CalendarScheduleConflictException.class)
			.hasMessage("일정이 연결된 캘린더는 삭제할 수 없습니다.");
		assertThat(((CalendarScheduleConflictException) thrown).getErrorCode())
			.isEqualTo(ErrorCode.CALENDAR_SCHEDULE_CONFLICT);

		verify(scheduleStore).existsByCalendarId("CALENDAR-0001");
		verify(calendarStore, never()).delete(any(Calendar.class));
	}

	@Test
	void deleteCalendar_shouldConvertDataIntegrityViolationToCalendarScheduleConflict() {
		Calendar calendar = Calendar.builder().build();
		when(calendarStore.findById("CALENDAR-0001")).thenReturn(calendar);
		when(scheduleStore.existsByCalendarId("CALENDAR-0001")).thenReturn(false);
		doThrow(new DataIntegrityViolationException("fk_schedule_calendar"))
			.when(calendarStore).delete(calendar);

		Throwable thrown = catchThrowable(() -> calendarLogic.deleteCalendar("CALENDAR-0001"));

		assertThat(thrown)
			.isInstanceOf(CalendarScheduleConflictException.class)
			.hasMessage("일정이 연결된 캘린더는 삭제할 수 없습니다.");
		assertThat(((CalendarScheduleConflictException) thrown).getErrorCode())
			.isEqualTo(ErrorCode.CALENDAR_SCHEDULE_CONFLICT);
	}
}
