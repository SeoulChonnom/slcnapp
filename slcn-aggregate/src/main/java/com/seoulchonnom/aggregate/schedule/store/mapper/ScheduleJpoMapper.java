package com.seoulchonnom.aggregate.schedule.store.mapper;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.schedule.store.jpo.ScheduleJpo;
import com.seoulchonnom.spec.schedule.entity.Schedule;

@Component
public class ScheduleJpoMapper {
	public ScheduleJpo toJpo(Schedule schedule) {
		ScheduleJpo scheduleJpo = new ScheduleJpo();
		scheduleJpo.setId(schedule.getId());
		scheduleJpo.setEntityVersion(schedule.getEntityVersion());
		scheduleJpo.setRegisteredTime(schedule.getRegisteredTime());
		scheduleJpo.setModifiedTime(schedule.getModifiedTime());
		scheduleJpo.setCalendarId(schedule.getCalendarId());
		scheduleJpo.setTitle(schedule.getTitle());
		scheduleJpo.setBody(schedule.getBody());
		scheduleJpo.setAllDay(schedule.isAllDay());
		scheduleJpo.setStart(schedule.getStart());
		scheduleJpo.setEnd(schedule.getEnd());
		scheduleJpo.setLocation(schedule.getLocation());
		scheduleJpo.setHidden(schedule.isHidden());
		return scheduleJpo;
	}

	public Schedule toDomain(ScheduleJpo scheduleJpo) {
		Schedule schedule = Schedule.builder()
			.calendarId(scheduleJpo.getCalendarId())
			.title(scheduleJpo.getTitle())
			.body(scheduleJpo.getBody())
			.allDay(scheduleJpo.isAllDay())
			.start(scheduleJpo.getStart())
			.end(scheduleJpo.getEnd())
			.location(scheduleJpo.getLocation())
			.hidden(scheduleJpo.isHidden())
			.build();
		schedule.setId(scheduleJpo.getId());
		schedule.setEntityVersion(scheduleJpo.getEntityVersion());
		if (scheduleJpo.getRegisteredTime() != null) {
			schedule.setRegisteredTime(scheduleJpo.getRegisteredTime());
		}
		if (scheduleJpo.getModifiedTime() != null) {
			schedule.setModifiedTime(scheduleJpo.getModifiedTime());
		}
		return schedule;
	}
}
