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
		scheduleRdo.setLocation(schedule.getLocation());
		return scheduleRdo;
	}

	private String formatStartDateTime(Schedule schedule) {
		if (schedule.getStart() == null) {
			return null;
		}

		return formatScheduleDateTime(schedule.getStart(), schedule.isAllDay());
	}

	private String formatEndDateTime(Schedule schedule) {
		if (schedule.getEnd() == null) {
			return null;
		}

		return formatScheduleDateTime(schedule.getEnd(), schedule.isAllDay());
	}
}
