package com.seoulchonnom.aggregate.schedule.store.jpo;

import java.time.LocalDateTime;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "schedule", schema = "slcn")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleJpo extends DomainEntityJpo {

	private String calendarId;

	private String title;

	private String body;

	private boolean isAllDay;

	@Column(name = "start_time")
	private LocalDateTime start;

	@Column(name = "end_time")
	private LocalDateTime end;

	private String location;

	private boolean hidden;
}
