package com.seoulchonnom.aggregate.schedule.store.jpo;

import java.time.LocalDateTime;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.spec.schedule.entity.ScheduleCategory;
import com.seoulchonnom.spec.schedule.entity.ScheduleState;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "schedule", schema = "slcn")
public class ScheduleJpo extends DomainEntityJpo {

	private String calendarId;

	private String title;

	private String body;

	private boolean isAllDay;

	@jakarta.persistence.Column(name = "start_time")
	private LocalDateTime start;

	@jakarta.persistence.Column(name = "end_time")
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
