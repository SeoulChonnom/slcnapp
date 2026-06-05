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
@Schema(description = "알림 송신 DTO")
public class ScheduleSearchSdo {
	@Schema(description = "검색 시작 일시(ISO 8601)", example = "2026-04-01T00:00:00+09:00")
	private String start;
	@Schema(description = "검색 종료 일시(ISO 8601)", example = "2026-05-01T00:00:00+09:00")
	private String end;
}
