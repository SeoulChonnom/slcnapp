package com.seoulchonnom.aggregate.schedule.logic;

import static com.seoulchonnom.spec.schedule.constant.ScheduleConstant.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.schedule.exception.InvalidScheduleDateException;
import com.seoulchonnom.aggregate.schedule.exception.InvalidScheduleRegisterRequestException;
import com.seoulchonnom.aggregate.schedule.store.ScheduleStore;
import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleCdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleRdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleUdo;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ScheduleLogic {
	private final ScheduleStore scheduleStore;

	public List<ScheduleRdo> getSchedulesForNow() {
		LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

		int year = now.getYear();
		int month = now.getMonthValue();

		return getSchedulesForMonth(year, month);
	}

	public List<ScheduleRdo> getSchedulesForMonth(int year, int month) {
		if (!isValidDate(year, month)) {
			throw new InvalidScheduleDateException();
		}

		LocalDateTime startDate = LocalDateTime.of(year, month, 1, 0, 0, 0);
		LocalDateTime endDate = startDate.plusMonths(1);

		List<Schedule> scheduleList = scheduleStore.findAllByStartBetweenAndIsVisible(startDate, endDate, true);

		return scheduleList.stream().map(Schedule::toRdo).toList();
	}

	public String registerSchedule(ScheduleCdo scheduleCdo) {
		if (!isValidDateTime(scheduleCdo.getStart()) || !isValidDateTime(scheduleCdo.getEnd())) {
			throw new InvalidScheduleRegisterRequestException();
		}

		Schedule schedule = new Schedule(scheduleCdo);

		scheduleStore.save(schedule);
		return schedule.getId();
	}

	public void modifySchedule(ScheduleUdo scheduleUdo) {
		if (!isValidDateTime(scheduleUdo.getStart()) || !isValidDateTime(scheduleUdo.getEnd())) {
			throw new InvalidScheduleRegisterRequestException();
		}

		Schedule schedule = scheduleStore.findById(scheduleUdo.getId());
		schedule.updateSchedule(scheduleUdo);

		scheduleStore.save(schedule);
	}

	public void hideSchedule(String scheduleId) {
		Schedule schedule = scheduleStore.findById(scheduleId);
		schedule.hideSchedule();
		scheduleStore.save(schedule);
	}

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

	private boolean isValidDateTime(String date) {
		try {
			LocalDateTime.parse(date, DATE_TIME_FORMATTER);
		} catch (DateTimeParseException e) {
			return false;
		}

		return true;
	}
}
