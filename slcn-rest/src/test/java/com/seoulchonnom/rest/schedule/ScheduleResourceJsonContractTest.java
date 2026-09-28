package com.seoulchonnom.rest.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.seoulchonnom.aggregate.schedule.logic.ScheduleLogic;
import com.seoulchonnom.rest.common.handler.CommonExceptionHandler;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleCdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleRdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleUdo;

class ScheduleResourceJsonContractTest {
	private static final String RECURRENCE_RULE = "FREQ=WEEKLY;BYDAY=TU";

	private ScheduleLogic scheduleLogic;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		scheduleLogic = mock(ScheduleLogic.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new ScheduleResource(scheduleLogic))
			.setControllerAdvice(new CommonExceptionHandler())
			.build();
	}

	@Test
	void registerSchedule_shouldBindRecurrenceRuleAndSerializeAllResponseFields() throws Exception {
		when(scheduleLogic.registerSchedule(any(ScheduleCdo.class))).thenReturn(recurringResponse());

		mockMvc.perform(post("/schedule")
				.contentType(MediaType.APPLICATION_JSON)
				.content(scheduleJson()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("SCHEDULE-0001"))
			.andExpect(jsonPath("$.calendarId").value("CALENDAR-0001"))
			.andExpect(jsonPath("$.title").value("주간 약속"))
			.andExpect(jsonPath("$.body").value(""))
			.andExpect(jsonPath("$.start").value("2026-09-01T19:00:00+09:00"))
			.andExpect(jsonPath("$.end").value("2026-09-01T20:00:00+09:00"))
			.andExpect(jsonPath("$.allDay").value(false))
			.andExpect(jsonPath("$.location").value(""))
			.andExpect(jsonPath("$.recurrenceRule").value(RECURRENCE_RULE))
			.andExpect(jsonPath("$.occurrenceId").value(nullValue()));

		ArgumentCaptor<ScheduleCdo> captor = ArgumentCaptor.forClass(ScheduleCdo.class);
		verify(scheduleLogic).registerSchedule(captor.capture());
		assertEquals("CALENDAR-0001", captor.getValue().getCalendarId());
		assertEquals("주간 약속", captor.getValue().getTitle());
		assertEquals("", captor.getValue().getBody());
		assertEquals("2026-09-01T19:00:00+09:00", captor.getValue().getStart());
		assertEquals("2026-09-01T20:00:00+09:00", captor.getValue().getEnd());
		assertEquals(false, captor.getValue().isAllDay());
		assertEquals("", captor.getValue().getLocation());
		assertEquals(RECURRENCE_RULE, captor.getValue().getRecurrenceRule());
	}

	@Test
	void modifySchedule_shouldBindRecurrenceRuleAndSerializeAllResponseFields() throws Exception {
		when(scheduleLogic.modifySchedule(any(ScheduleUdo.class))).thenReturn(recurringResponse());

		mockMvc.perform(put("/schedule")
				.contentType(MediaType.APPLICATION_JSON)
				.content(scheduleJson()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("SCHEDULE-0001"))
			.andExpect(jsonPath("$.calendarId").value("CALENDAR-0001"))
			.andExpect(jsonPath("$.title").value("주간 약속"))
			.andExpect(jsonPath("$.body").value(""))
			.andExpect(jsonPath("$.start").value("2026-09-01T19:00:00+09:00"))
			.andExpect(jsonPath("$.end").value("2026-09-01T20:00:00+09:00"))
			.andExpect(jsonPath("$.allDay").value(false))
			.andExpect(jsonPath("$.location").value(""))
			.andExpect(jsonPath("$.recurrenceRule").value(RECURRENCE_RULE))
			.andExpect(jsonPath("$.occurrenceId").value(nullValue()));

		ArgumentCaptor<ScheduleUdo> captor = ArgumentCaptor.forClass(ScheduleUdo.class);
		verify(scheduleLogic).modifySchedule(captor.capture());
		assertEquals("CALENDAR-0001", captor.getValue().getCalendarId());
		assertEquals("주간 약속", captor.getValue().getTitle());
		assertEquals("", captor.getValue().getBody());
		assertEquals("2026-09-01T19:00:00+09:00", captor.getValue().getStart());
		assertEquals("2026-09-01T20:00:00+09:00", captor.getValue().getEnd());
		assertEquals(false, captor.getValue().isAllDay());
		assertEquals("", captor.getValue().getLocation());
		assertEquals(RECURRENCE_RULE, captor.getValue().getRecurrenceRule());
	}

	private ScheduleRdo recurringResponse() {
		ScheduleRdo response = new ScheduleRdo();
		response.setId("SCHEDULE-0001");
		response.setCalendarId("CALENDAR-0001");
		response.setTitle("주간 약속");
		response.setBody("");
		response.setStart("2026-09-01T19:00:00+09:00");
		response.setEnd("2026-09-01T20:00:00+09:00");
		response.setAllDay(false);
		response.setLocation("");
		response.setRecurrenceRule(RECURRENCE_RULE);
		return response;
	}

	private String scheduleJson() {
		return """
			{
			  "calendarId": "CALENDAR-0001",
			  "title": "주간 약속",
			  "body": "",
			  "start": "2026-09-01T19:00:00+09:00",
			  "end": "2026-09-01T20:00:00+09:00",
			  "allDay": false,
			  "location": "",
			  "recurrenceRule": "FREQ=WEEKLY;BYDAY=TU"
			}
			""";
	}
}
