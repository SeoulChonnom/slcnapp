package com.seoulchonnom.aggregate.schedule.logic;

import static com.seoulchonnom.spec.schedule.constant.ScheduleConstant.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ScheduleLogic {
	private final CalendarStore calendarStore;
	private final ScheduleStore scheduleStore;
	private final ScheduleMapper scheduleMapper;

	public List<ScheduleRdo> getSchedulesForNow() {
		LocalDateTime now = LocalDateTime.now(SCHEDULE_ZONE_ID);

		int year = now.getYear();
		int month = now.getMonthValue();

		return getSchedulesForMonth(year, month);
	}

	public List<ScheduleRdo> getSchedules(ScheduleSearchSdo scheduleSearchSdo) {
		boolean hasStart = StringUtils.hasText(scheduleSearchSdo.getStart());
		boolean hasEnd = StringUtils.hasText(scheduleSearchSdo.getEnd());

		if (!hasStart || !hasEnd) {
			throw new InvalidScheduleDateException();
		}

		return getSchedulesForRange(scheduleSearchSdo.getStart(), scheduleSearchSdo.getEnd());
	}

	public List<ScheduleRdo> getSchedulesForMonth(int year, int month) {
		if (!isValidDate(year, month)) {
			throw new InvalidScheduleDateException();
		}

		LocalDateTime startDate = LocalDateTime.of(year, month, 1, 0, 0, 0);
		LocalDateTime endDate = startDate.plusMonths(1);

		List<Schedule> scheduleList = scheduleStore.findAllByDateRange(startDate, endDate);

		return scheduleList.stream()
			.map(scheduleMapper::toScheduleRdo)
			.toList();
	}

	public List<ScheduleRdo> getSchedulesForRange(String start, String end) {
		LocalDateTime startDateTime = parseSearchDateTime(start);
		LocalDateTime endDateTime = parseSearchDateTime(end);

		if (!startDateTime.isBefore(endDateTime)) {
			throw new InvalidScheduleDateException();
		}

		if (endDateTime.isAfter(startDateTime.plusMonths(1))) {
			throw new InvalidScheduleDateException();
		}

		List<Schedule> scheduleList = scheduleStore.findAllByDateRange(startDateTime, endDateTime);

		return scheduleList.stream()
			.map(scheduleMapper::toScheduleRdo)
			.toList();
	}

	@Transactional
	public ScheduleRdo registerSchedule(ScheduleCdo scheduleCdo) {
		validateScheduleMutation(scheduleCdo.getCalendarId(), scheduleCdo.getTitle(), scheduleCdo.isAllDay(),
			scheduleCdo.getStart(), scheduleCdo.getEnd());

		Schedule schedule = scheduleMapper.toSchedule(scheduleCdo);

		scheduleStore.save(schedule);
		return scheduleMapper.toScheduleRdo(schedule);
	}

	@Transactional
	public ScheduleRdo modifySchedule(ScheduleUdo scheduleUdo) {
		validateScheduleMutation(scheduleUdo.getCalendarId(), scheduleUdo.getTitle(), scheduleUdo.isAllDay(),
			scheduleUdo.getStart(), scheduleUdo.getEnd());

		Schedule schedule = scheduleStore.findById(scheduleUdo.getId());
		scheduleMapper.updateSchedule(scheduleUdo, schedule);

		scheduleStore.save(schedule);
		return scheduleMapper.toScheduleRdo(schedule);
	}

	@Transactional
	public void hideSchedule(String scheduleId) {
		Schedule schedule = scheduleStore.findById(scheduleId);
		schedule.hideSchedule();
		scheduleStore.save(schedule);
	}

	@Transactional
	public void deleteSchedule(String scheduleId) {
		Schedule schedule = scheduleStore.findById(scheduleId);
		scheduleStore.delete(schedule);
	}

	private boolean isValidDate(int year, int month) {
		if (year < 1900 || year > 2100) {
			return false;
		}

		return month >= 1 && month <= 12;
	}

	private LocalDateTime parseSearchDateTime(String date) {
		try {
			return parseMutationDateTime(date);
		} catch (DateTimeParseException e) {
			throw new InvalidScheduleDateException();
		}
	}

	private LocalDateTime parseMutationRequestDateTime(String date, boolean isAllDay) {
		try {
			return parseMutationDateTime(date, isAllDay);
		} catch (DateTimeParseException e) {
			throw new InvalidScheduleRegisterRequestException();
		}
	}

	private void validateScheduleMutation(String calendarId, String title, boolean allDay, String start, String end) {
		if (!StringUtils.hasText(calendarId)) {
			throw new BadRequestException("calendarId는 필수입니다.");
		}

		if (!calendarStore.existsVisibleById(calendarId)) {
			throw new BadRequestException("사용할 수 없는 calendarId입니다.");
		}

		if (!StringUtils.hasText(title)) {
			throw new BadRequestException("title은 필수입니다.");
		}

		LocalDateTime startDateTime = parseMutationRequestDateTime(start, allDay);
		LocalDateTime endDateTime = parseMutationRequestDateTime(end, allDay);

		if (startDateTime.isAfter(endDateTime)) {
			throw new BadRequestException("start는 end보다 빨라야 합니다.");
		}
	}
}
