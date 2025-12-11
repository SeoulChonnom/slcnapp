package com.seoulchonnom.spec.schedule.entity;

import java.time.LocalDateTime;

import com.seoulchonnom.spec.common.entity.DomainEntity;

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

	private Long goingDuration;
	private Long comingDuration;

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
}
