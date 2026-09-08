package com.seoulchonnom.rest.schedule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.seoulchonnom.aggregate.schedule.logic.ScheduleLogic;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleCdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleRdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleSearchSdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleUdo;

class ScheduleResourceTest {
	private ScheduleLogic scheduleLogic;
	private ScheduleResource scheduleResource;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		scheduleLogic = mock(ScheduleLogic.class);
		scheduleResource = new ScheduleResource(scheduleLogic);
		mockMvc = MockMvcBuilders.standaloneSetup(scheduleResource).build();
	}

	@Test
	void getSchedulesForYearAndMonth_shouldDelegateToScheduleLogic() {
		ScheduleSearchSdo searchSdo = new ScheduleSearchSdo("2026-04-01T00:00:00+09:00", "2026-05-01T00:00:00+09:00");
		List<ScheduleRdo> scheduleList = List.of(new ScheduleRdo());
		when(scheduleLogic.getSchedules(searchSdo)).thenReturn(scheduleList);

		ResponseEntity<List<ScheduleRdo>> response = scheduleResource.getSchedulesForYearAndMonth(searchSdo);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(scheduleList, response.getBody());
		verify(scheduleLogic).getSchedules(searchSdo);
	}

	@Test
	void registerSchedule_shouldReturnMappedSchedule() {
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		ScheduleRdo scheduleRdo = new ScheduleRdo();
		when(scheduleLogic.registerSchedule(scheduleCdo)).thenReturn(scheduleRdo);

		ResponseEntity<ScheduleRdo> response = scheduleResource.registerSchedule(scheduleCdo);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(scheduleRdo, response.getBody());
		verify(scheduleLogic).registerSchedule(scheduleCdo);
	}

	@Test
	void modifySchedule_shouldReturnUpdatedSchedule() {
		ScheduleUdo scheduleUdo = new ScheduleUdo();
		ScheduleRdo scheduleRdo = new ScheduleRdo();
		when(scheduleLogic.modifySchedule(scheduleUdo)).thenReturn(scheduleRdo);

		ResponseEntity<ScheduleRdo> response = scheduleResource.modifySchedule(scheduleUdo);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(scheduleRdo, response.getBody());
		verify(scheduleLogic).modifySchedule(scheduleUdo);
	}

	@Test
	void deleteSchedule_shouldReturnNoContent() throws Exception {
		doNothing().when(scheduleLogic).deleteSchedule("SCHEDULE-0001");

		mockMvc.perform(delete("/schedule/{scheduleId}", "SCHEDULE-0001"))
			.andExpect(status().isNoContent());

		verify(scheduleLogic).deleteSchedule("SCHEDULE-0001");
	}

	@Test
	void hideSchedule_shouldNotBeMapped() throws Exception {
		mockMvc.perform(put("/schedule/{scheduleId}/hide", "SCHEDULE-0001"))
			.andExpect(status().isNotFound());
	}
}
