package com.seoulchonnom.rest.calendar;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.seoulchonnom.aggregate.calendar.logic.CalendarLogic;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarCdo;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarRdo;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarUdo;

class CalendarResourceTest {
	@Test
	void getCalendars_shouldDelegateToCalendarLogic() {
		CalendarLogic calendarLogic = mock(CalendarLogic.class);
		CalendarResource calendarResource = new CalendarResource(calendarLogic);
		List<CalendarRdo> calendarList = List.of(new CalendarRdo());
		when(calendarLogic.getCalendars()).thenReturn(calendarList);

		ResponseEntity<List<CalendarRdo>> response = calendarResource.getCalendars();

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(calendarList, response.getBody());
		verify(calendarLogic).getCalendars();
	}

	@Test
	void registerCalendar_shouldReturnMappedCalendar() {
		CalendarLogic calendarLogic = mock(CalendarLogic.class);
		CalendarResource calendarResource = new CalendarResource(calendarLogic);
		CalendarCdo calendarCdo = new CalendarCdo();
		CalendarRdo calendarRdo = new CalendarRdo();
		when(calendarLogic.registerCalendar(calendarCdo)).thenReturn(calendarRdo);

		ResponseEntity<CalendarRdo> response = calendarResource.registerCalendar(calendarCdo);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(calendarRdo, response.getBody());
	}

	@Test
	void modifyCalendar_shouldReturnUpdatedCalendar() {
		CalendarLogic calendarLogic = mock(CalendarLogic.class);
		CalendarResource calendarResource = new CalendarResource(calendarLogic);
		CalendarUdo calendarUdo = new CalendarUdo();
		CalendarRdo calendarRdo = new CalendarRdo();
		when(calendarLogic.modifyCalendar(calendarUdo)).thenReturn(calendarRdo);

		ResponseEntity<CalendarRdo> response = calendarResource.modifyCalendar(calendarUdo);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(calendarRdo, response.getBody());
	}

	@Test
	void hideAndDelete_shouldReturnNoContent() {
		CalendarLogic calendarLogic = mock(CalendarLogic.class);
		CalendarResource calendarResource = new CalendarResource(calendarLogic);

		ResponseEntity<Void> hideResponse = calendarResource.hideCalendar("CALENDAR-0001");
		ResponseEntity<Void> deleteResponse = calendarResource.deleteCalendar("CALENDAR-0001");

		assertEquals(HttpStatus.NO_CONTENT, hideResponse.getStatusCode());
		assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());
		verify(calendarLogic).hideCalendar("CALENDAR-0001");
		verify(calendarLogic).deleteCalendar("CALENDAR-0001");
	}
}
