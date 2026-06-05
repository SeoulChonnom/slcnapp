package com.seoulchonnom.spec.schedule.facade.sdo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleCdo {
	@Schema(description = "캘린더 ID", example = "cal1")
	private String calendarId;
	@Schema(description = "제목", example = "title")
	private String title;
	@Schema(description = "설명", example = "body")
	private String body;
	@Schema(description = "시작 일시(ISO 8601, 종일 일정은 yyyy-MM-dd)", example = "2025-03-01T00:00:00+09:00")
	private String start;
	@Schema(description = "종료 일시(ISO 8601, 종일 일정은 yyyy-MM-dd)", example = "2025-03-01T01:00:00+09:00")
	private String end;
	@Schema(description = "종일 일정 여부", example = "false")
	private boolean allDay;
	@Schema(description = "장소", example = "location")
	private String location;
}
