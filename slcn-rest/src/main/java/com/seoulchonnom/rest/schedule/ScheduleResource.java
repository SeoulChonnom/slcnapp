package com.seoulchonnom.rest.schedule;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.schedule.logic.ScheduleLogic;
import com.seoulchonnom.spec.schedule.facade.ScheduleFacade;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleCdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleRdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleSearchSdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleUdo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleResource implements ScheduleFacade {
	private final ScheduleLogic scheduleLogic;

	@Override
	@GetMapping("/now")
	public ResponseEntity<List<ScheduleRdo>> getSchedulesForNow() {
		return new ResponseEntity<>(scheduleLogic.getSchedulesForNow(), HttpStatus.OK);
	}

	@Override
	@GetMapping
	public ResponseEntity<List<ScheduleRdo>> getSchedulesForYearAndMonth(@ModelAttribute ScheduleSearchSdo scheduleSearchSdo) {
		return new ResponseEntity<>(
			scheduleLogic.getSchedulesForMonth(scheduleSearchSdo.getYear(), scheduleSearchSdo.getMonth()),
			HttpStatus.OK);
	}

	@Override
	@PostMapping
	public ResponseEntity<ScheduleRdo> registerSchedule(@RequestBody ScheduleCdo scheduleCdo) {
		return new ResponseEntity<>(scheduleLogic.registerSchedule(scheduleCdo), HttpStatus.OK);
	}

	@Override
	@PutMapping
	public ResponseEntity<ScheduleRdo> modifySchedule(@RequestBody ScheduleUdo scheduleUdo) {
		return new ResponseEntity<>(scheduleLogic.modifySchedule(scheduleUdo), HttpStatus.OK);
	}

	@Override
	@PatchMapping("/{scheduleId}/hide")
	public ResponseEntity<Void> hideSchedule(@PathVariable("scheduleId") String scheduleId) {
		scheduleLogic.hideSchedule(scheduleId);
		return ResponseEntity.noContent().build();
	}

	@Override
	@DeleteMapping("/{scheduleId}")
	public ResponseEntity<Void> deleteSchedule(@PathVariable("scheduleId") String scheduleId) {
		scheduleLogic.deleteSchedule(scheduleId);
		return ResponseEntity.noContent().build();
	}
}
