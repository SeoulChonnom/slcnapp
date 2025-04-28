package com.seoulchonnom.slcnapp.schedule.controller;

import com.seoulchonnom.slcnapp.common.dto.BaseResponse;
import com.seoulchonnom.slcnapp.schedule.dto.ScheduleModifyRequest;
import com.seoulchonnom.slcnapp.schedule.dto.ScheduleRegisterRequest;
import com.seoulchonnom.slcnapp.schedule.dto.ScheduleSearchRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;

@Tag(name = "일정 정보 API", description = "일정 정보 조회")
public interface ScheduleControllerDocs {

	@Parameters({
		@Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)
	})
	@Operation(summary = "이번달 일정 조회", description = "달력페이지 진입시 이번달 일정 조회 API")
	ResponseEntity<BaseResponse> getSchedulesForNow();

	@Parameters({
		@Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)
	})
	@Operation(summary = "특정 달 일정 조회", description = "달력 이동 시 해당 달의 일정 조회 API")
	ResponseEntity<BaseResponse> getSchedulesForYearAndMonth(ScheduleSearchRequest scheduleSearchRequest);

	@Parameters({
		@Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)
	})
	@Operation(summary = "일정 등록", description = "일정 등록 API")
	ResponseEntity<BaseResponse> registerSchedule(ScheduleRegisterRequest scheduleSearchRequest);

	@Parameters({
		@Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)
	})
	@Operation(summary = "일정 수정", description = "일정 수정 API")
	ResponseEntity<BaseResponse> modifySchedule(ScheduleModifyRequest scheduleModifyRequest);
}
