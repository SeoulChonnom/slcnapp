package com.seoulchonnom.aggregate.schedule.store.jpo;

import java.time.LocalDateTime;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.spec.schedule.entity.ScheduleCategory;
import com.seoulchonnom.spec.schedule.entity.ScheduleState;

import jakarta.persistence.Entity;

@Entity
public class ScheduleJpo extends DomainEntityJpo {

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
}
