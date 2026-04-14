package com.seoulchonnom.spec.schedule.facade.sdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ScheduleRdo {
	private String id;
	private String calendarId;
	private String title;
	private String body;
	private String start;
	private String end;
	private boolean allDay;
	private String location;
}
