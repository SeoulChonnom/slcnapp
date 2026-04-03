package com.seoulchonnom.spec.schedule.entity;

import static com.seoulchonnom.spec.schedule.constant.ScheduleConstant.*;

import java.time.LocalDateTime;

import org.springframework.beans.BeanUtils;

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

	private boolean isAllDay;

	private LocalDateTime start;

	private LocalDateTime end;

	private long goingDuration;
	private long comingDuration;

	private String location;

	private ScheduleCategory category;

	private String dueDateClass;
	private String recurrenceRule;

	private ScheduleState state;

	private boolean isVisible;
	private boolean isPending;
	private boolean isFocused;
	private boolean isReadOnly;
	private boolean isPrivate;

	private String color;
	private String backgroundColor;
	private String dragBackgroundColor;
	private String borderColor;
	private String customStyle;

	public Schedule(ScheduleCdo scheduleCdo) {
		super();
		BeanUtils.copyProperties(scheduleCdo, this, "start", "end");
		this.start = LocalDateTime.parse(scheduleCdo.getStart(), DATE_TIME_FORMATTER);
		this.end = LocalDateTime.parse(scheduleCdo.getEnd(), DATE_TIME_FORMATTER);
	}

	public void updateSchedule(ScheduleUdo scheduleUdo) {
		BeanUtils.copyProperties(scheduleUdo, this, "start", "end");
		this.start = LocalDateTime.parse(scheduleUdo.getStart(), DATE_TIME_FORMATTER);
		this.end = LocalDateTime.parse(scheduleUdo.getEnd(), DATE_TIME_FORMATTER);
	}

	public void hideSchedule() {
		this.isVisible = false;
	}
}
