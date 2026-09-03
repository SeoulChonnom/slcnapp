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

	private boolean allDay;

	private LocalDateTime start;

	private LocalDateTime end;

	private String location;

	private String recurrenceRule;

	private boolean hidden;

	public Schedule(
		String calendarId,
		String title,
		String body,
		boolean allDay,
		LocalDateTime start,
		LocalDateTime end,
		String location,
		boolean hidden
	) {
		this(calendarId, title, body, allDay, start, end, location, null, hidden);
	}

	public void hideSchedule() {
		this.hidden = true;
	}

	public void touchModifiedTime() {
		this.modifiedTime = System.currentTimeMillis();
	}
}
