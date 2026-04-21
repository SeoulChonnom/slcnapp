package com.seoulchonnom.spec.calendar.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.calendar.facade.sdo.CalendarCdo;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarRdo;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarUdo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "캘린더 정보 API", description = "캘린더 정보 관리")
@SecurityRequirement(name = "X-AUTH-TOKEN")
public interface CalendarFacade {
	@Operation(summary = "캘린더 목록 조회", description = "표시 가능한 캘린더 목록 조회 API")
	ResponseEntity<List<CalendarRdo>> getCalendars();

	@Operation(summary = "캘린더 등록", description = "캘린더 등록 API")
	ResponseEntity<CalendarRdo> registerCalendar(CalendarCdo calendarCdo);

	@Operation(summary = "캘린더 수정", description = "캘린더 수정 API")
	ResponseEntity<CalendarRdo> modifyCalendar(CalendarUdo calendarUdo);

	@Operation(summary = "캘린더 삭제(숨김)", description = "캘린더 목록 삭제 API")
	ResponseEntity<Void> hideCalendar(String calendarId);

	@Operation(summary = "캘린더 완전 삭제", description = "캘린더 데이터 삭제 API")
	ResponseEntity<Void> deleteCalendar(String calendarId);
}
