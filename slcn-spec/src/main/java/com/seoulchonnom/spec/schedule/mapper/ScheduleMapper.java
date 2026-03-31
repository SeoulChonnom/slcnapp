package com.seoulchonnom.spec.schedule.mapper;

import static com.seoulchonnom.spec.schedule.constant.ScheduleConstant.*;

import org.springframework.stereotype.Component;

import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleRdo;

@Component
public class ScheduleMapper {
	public ScheduleRdo toScheduleRdo(Schedule schedule) {
		ScheduleRdo scheduleRdo = new ScheduleRdo();
		scheduleRdo.setId(schedule.getId());
		scheduleRdo.setCalendarId(schedule.getCalendarId());
		scheduleRdo.setTitle(schedule.getTitle());
		scheduleRdo.setBody(schedule.getBody());
		scheduleRdo.setAllDay(schedule.isAllDay());
		scheduleRdo.setStart(formatStartDateTime(schedule));
		scheduleRdo.setEnd(formatEndDateTime(schedule));
		scheduleRdo.setGoingDuration(schedule.getGoingDuration());
		scheduleRdo.setComingDuration(schedule.getComingDuration());
		scheduleRdo.setLocation(schedule.getLocation());
		scheduleRdo.setCategory(schedule.getCategory());
		scheduleRdo.setDueDateClass(schedule.getDueDateClass());
		scheduleRdo.setRecurrenceRule(schedule.getRecurrenceRule());
		scheduleRdo.setState(schedule.getState());
		scheduleRdo.setVisible(schedule.isVisible());
		scheduleRdo.setPending(schedule.isPending());
		scheduleRdo.setFocused(schedule.isFocused());
		scheduleRdo.setReadOnly(schedule.isReadOnly());
		scheduleRdo.setPrivate(schedule.isPrivate());
		scheduleRdo.setColor(schedule.getColor());
		scheduleRdo.setBackgroundColor(schedule.getBackgroundColor());
		scheduleRdo.setDragBackgroundColor(schedule.getDragBackgroundColor());
		scheduleRdo.setBorderColor(schedule.getBorderColor());
		scheduleRdo.setCustomStyle(schedule.getCustomStyle());
		return scheduleRdo;
	}

	private String formatStartDateTime(Schedule schedule) {
		if (schedule.getStart() == null) {
			return null;
		}

		return schedule.getStart().format(DATE_TIME_FORMATTER);
	}

	private String formatEndDateTime(Schedule schedule) {
		if (schedule.getEnd() == null) {
			return null;
		}

		return schedule.getEnd().format(DATE_TIME_FORMATTER);
	}
}
