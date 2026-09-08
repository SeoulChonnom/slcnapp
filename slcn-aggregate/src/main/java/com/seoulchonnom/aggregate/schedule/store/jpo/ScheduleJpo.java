package com.seoulchonnom.aggregate.schedule.store.jpo;

import java.time.LocalDateTime;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
	name = "schedule",
	schema = "slcn",
	indexes = {
		@Index(name = "idx_schedule_start_end", columnList = "start_time,end_time"),
		@Index(name = "idx_schedule_calendar_id", columnList = "calendar_id")
	}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleJpo extends DomainEntityJpo {

	private String calendarId;

	private String title;

	private String body;

	private boolean isAllDay;

	@Column(name = "start_time", nullable = false)
	private LocalDateTime start;

	@Column(name = "end_time", nullable = false)
	private LocalDateTime end;

	private String location;

	@Column(name = "recurrence_rule", columnDefinition = "text")
	private String recurrenceRule;
}
