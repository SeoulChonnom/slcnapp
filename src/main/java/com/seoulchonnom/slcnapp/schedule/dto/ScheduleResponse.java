package com.seoulchonnom.slcnapp.schedule.dto;

import com.seoulchonnom.slcnapp.schedule.domain.Schedule;
import com.seoulchonnom.slcnapp.schedule.domain.ScheduleCategory;
import com.seoulchonnom.slcnapp.schedule.domain.ScheduleState;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScheduleResponse {
	private String id;
	private String calendarId;
	private String title;
	private String body;
	private boolean isAllDay;
	private String start;
	private String end;
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

	public static ScheduleResponse from(Schedule schedule) {
		return ScheduleResponse.builder()
			.id(schedule.getId())
			.calendarId(schedule.getCalendarId())
			.title(schedule.getTitle())
			.body(schedule.getBody())
			.isAllDay(schedule.isAllDay())
			.start(schedule.getStart().toString())
			.end(schedule.getEnd().toString())
			.goingDuration(schedule.getGoingDuration())
			.comingDuration(schedule.getComingDuration())
			.location(schedule.getLocation())
			.category(schedule.getCategory())
			.dueDateClass(schedule.getDueDateClass())
			.recurrenceRule(schedule.getRecurrenceRule())
			.state(schedule.getState())
			.isVisible(schedule.isVisible())
			.isPending(schedule.isPending())
			.isFocused(schedule.isFocused())
			.isReadOnly(schedule.isReadOnly())
			.isPrivate(schedule.isPrivate())
			.color(schedule.getColor())
			.backgroundColor(schedule.getBackgroundColor())
			.dragBackgroundColor(schedule.getDragBackgroundColor())
			.borderColor(schedule.getBorderColor())
			.customStyle(schedule.getCustomStyle())
			.build();
	}
}
