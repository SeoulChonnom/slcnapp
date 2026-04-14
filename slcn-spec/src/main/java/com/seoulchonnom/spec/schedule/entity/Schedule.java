package com.seoulchonnom.spec.schedule.entity;

import static com.seoulchonnom.spec.schedule.constant.ScheduleConstant.*;

import java.time.LocalDateTime;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleCdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleUdo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
@Setter
public class Schedule extends DomainEntity {
	private String calendarId;

	private String title;

	private String body;

	private boolean allDay;

	private LocalDateTime start;

	private LocalDateTime end;

	private String location;

	private boolean hidden;

	public Schedule(ScheduleCdo scheduleCdo) {
		super();
		this.calendarId = scheduleCdo.getCalendarId();
		this.title = scheduleCdo.getTitle();
		this.body = scheduleCdo.getBody();
		this.allDay = scheduleCdo.isAllDay();
		this.start = parseMutationDateTime(scheduleCdo.getStart(), scheduleCdo.isAllDay());
		this.end = parseMutationDateTime(scheduleCdo.getEnd(), scheduleCdo.isAllDay());
		this.location = scheduleCdo.getLocation();
		this.hidden = false;
	}

	public void updateSchedule(ScheduleUdo scheduleUdo) {
		this.calendarId = scheduleUdo.getCalendarId();
		this.title = scheduleUdo.getTitle();
		this.body = scheduleUdo.getBody();
		this.allDay = scheduleUdo.isAllDay();
		this.start = parseMutationDateTime(scheduleUdo.getStart(), scheduleUdo.isAllDay());
		this.end = parseMutationDateTime(scheduleUdo.getEnd(), scheduleUdo.isAllDay());
		this.location = scheduleUdo.getLocation();
	}

	public void hideSchedule() {
		this.hidden = true;
	}
}
