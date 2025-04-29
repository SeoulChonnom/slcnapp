package com.seoulchonnom.slcnapp.schedule.controller;

import static com.seoulchonnom.slcnapp.schedule.ScheduleConstant.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.slcnapp.common.dto.BaseResponse;
import com.seoulchonnom.slcnapp.schedule.dto.ScheduleModifyRequest;
import com.seoulchonnom.slcnapp.schedule.dto.ScheduleRegisterRequest;
import com.seoulchonnom.slcnapp.schedule.dto.ScheduleSearchRequest;
import com.seoulchonnom.slcnapp.schedule.service.ScheduleService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleController implements ScheduleControllerDocs {

	private final ScheduleService scheduleService;

	@Override
	@GetMapping("/")
	public ResponseEntity<BaseResponse> getSchedulesForNow() {
		return new ResponseEntity<>(
			BaseResponse.from(true, RETRIEVE_SCHEDULE_COMPLETE_MESSAGE, scheduleService.getSchedulesForNow()),
			HttpStatus.OK);
	}

	@Override
	@GetMapping("/date")
	public ResponseEntity<BaseResponse> getSchedulesForYearAndMonth(ScheduleSearchRequest request) {
		return new ResponseEntity<>(
			BaseResponse.from(true, RETRIEVE_SCHEDULE_COMPLETE_MESSAGE,
				scheduleService.getSchedulesForMonth(request.getYear(), request.getMonth())),
			HttpStatus.OK);
	}

	@Override
	@PostMapping("/register")
	public ResponseEntity<BaseResponse> registerSchedule(@RequestBody ScheduleRegisterRequest request) {
		return new ResponseEntity<>(
			BaseResponse.from(true, REGISTER_SCHEDULE_COMPLETE_MESSAGE, scheduleService.registerSchedule(request)),
			HttpStatus.OK
		);
	}

	@Override
	@PutMapping("/modify")
	public ResponseEntity<BaseResponse> modifySchedule(@RequestBody ScheduleModifyRequest scheduleModifyRequest) {
		scheduleService.modifySchedule(scheduleModifyRequest);
		return new ResponseEntity<>(
			BaseResponse.from(true, MODIFY_SCHEDULE_COMPLETE_MESSAGE),
			HttpStatus.OK
		);
	}

	@Override
	@PutMapping("/remove/{scheduleId}")
	public ResponseEntity<BaseResponse> hideSchedule(@PathVariable String scheduleId) {
		scheduleService.hideSchedule(scheduleId);
		return new ResponseEntity<>(
			BaseResponse.from(true, DELETE_SCHEDULE_COMPLETE_MESSAGE),
			HttpStatus.OK
		);
	}

	@Override
	@DeleteMapping("/delete/{scheduleId}")
	public ResponseEntity<BaseResponse> deleteSchedule(@PathVariable String scheduleId) {
		scheduleService.deleteSchedule(scheduleId);
		return new ResponseEntity<>(
			BaseResponse.from(true, HARD_DELETE_SCHEDULE_COMPLETE_MESSAGE),
			HttpStatus.OK
		);
	}
}
