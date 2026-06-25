package com.seoulchonnom.rest.calendar;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.calendar.logic.CalendarLogic;
import com.seoulchonnom.spec.calendar.facade.CalendarFacade;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarCdo;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarRdo;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarUdo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/calendars")
@RequiredArgsConstructor
public class CalendarResource implements CalendarFacade {
	private final CalendarLogic calendarLogic;

	@Override
	@GetMapping
	public ResponseEntity<List<CalendarRdo>> getCalendars() {
		return new ResponseEntity<>(calendarLogic.getCalendars(), HttpStatus.OK);
	}

	@Override
	@PostMapping
	public ResponseEntity<CalendarRdo> registerCalendar(@RequestBody CalendarCdo calendarCdo) {
		return new ResponseEntity<>(calendarLogic.registerCalendar(calendarCdo), HttpStatus.OK);
	}

	@Override
	@PutMapping
	public ResponseEntity<CalendarRdo> modifyCalendar(@RequestBody CalendarUdo calendarUdo) {
		return new ResponseEntity<>(calendarLogic.modifyCalendar(calendarUdo), HttpStatus.OK);
	}

	@Override
	@PatchMapping("/{calendarId}/hide")
	public ResponseEntity<Void> hideCalendar(@PathVariable("calendarId") String calendarId) {
		calendarLogic.hideCalendar(calendarId);
		return ResponseEntity.noContent().build();
	}

	@Override
	@DeleteMapping("/{calendarId}")
	public ResponseEntity<Void> deleteCalendar(@PathVariable("calendarId") String calendarId) {
		calendarLogic.deleteCalendar(calendarId);
		return ResponseEntity.noContent().build();
	}
}
