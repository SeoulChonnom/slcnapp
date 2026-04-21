package com.seoulchonnom.spec.schedule.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleCdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleRdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleSearchSdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleUdo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "일정 정보 API", description = "일정 정보 조회")
@SecurityRequirement(name = "X-AUTH-TOKEN")
public interface ScheduleFacade {

	@Operation(summary = "이번달 일정 조회", description = "달력페이지 진입시 이번달 일정 조회 API")
	ResponseEntity<List<ScheduleRdo>> getSchedulesForNow();

	@Operation(summary = "일정 범위 조회", description = "보이는 범위와 겹치는 일정 조회 API")
	ResponseEntity<List<ScheduleRdo>> getSchedulesForYearAndMonth(ScheduleSearchSdo scheduleSearchSdo);

	@Operation(summary = "일정 등록", description = "일정 등록 API")
	ResponseEntity<ScheduleRdo> registerSchedule(ScheduleCdo scheduleCdo);

	@Operation(summary = "일정 수정", description = "일정 수정 API")
	ResponseEntity<ScheduleRdo> modifySchedule(ScheduleUdo scheduleUdo);

	@Operation(summary = "일정 삭제(숨김)", description = "일정 목록 삭제 API")
	ResponseEntity<Void> hideSchedule(String scheduleId);

	@Operation(summary = "일정 완전 삭제", description = "일정 데이터 삭제 API")
	ResponseEntity<Void> deleteSchedule(String scheduleId);
}
