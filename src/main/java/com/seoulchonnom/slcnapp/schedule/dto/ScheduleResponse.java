package com.seoulchonnom.slcnapp.schedule.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.seoulchonnom.slcnapp.schedule.domain.Schedule;
import com.seoulchonnom.slcnapp.schedule.domain.ScheduleCategory;
import com.seoulchonnom.slcnapp.schedule.domain.ScheduleState;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class ScheduleResponse {
	private String id;
	private String calendarId;
	private String title;
	private String body;

	@JsonProperty(value="isAllday")
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

	@JsonProperty(value="isVisible")
	private boolean isVisible;
	@JsonProperty(value="isPending")
	private boolean isPending;
	@JsonProperty(value="isFocused")
	private boolean isFocused;
	@JsonProperty(value="isReadOnly")
	private boolean isReadOnly;
	@JsonProperty(value="isPrivate")
	private boolean isPrivate;

	private String color;
	private String backgroundColor;
	private String dragBackgroundColor;
	private String borderColor;
	private String customStyle;

	@JsonIgnore
	public boolean isAllDay() {
		return isAllDay;
	}

	@JsonIgnore
	public boolean isVisible() {
		return isVisible;
	}

	@JsonIgnore
	public boolean isPending() {
		return isPending;
	}

	@JsonIgnore
	public boolean isFocused() {
		return isFocused;
	}

	@JsonIgnore
	public boolean isReadOnly() {
		return isReadOnly;
	}

	@JsonIgnore
	public boolean isPrivate() {
		return isPrivate;
	}

	public static ScheduleResponse from(Schedule schedule) {
		return ScheduleResponse.builder()
			.id(schedule.getId())
			.calendarId(schedule.getCalendarId())
			.title(schedule.getTitle())
			.body(schedule.getBody())
			.isAllDay(schedule.isAllDay())
			.start(schedule.getStart().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
			.end(schedule.getEnd().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
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
