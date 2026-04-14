package com.seoulchonnom.rest.schedule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.seoulchonnom.aggregate.schedule.logic.ScheduleLogic;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleCdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleRdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleSearchSdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleUdo;

class ScheduleResourceTest {
	@Test
	void getSchedulesForYearAndMonth_shouldDelegateToScheduleLogic() {
		ScheduleLogic scheduleLogic = mock(ScheduleLogic.class);
		ScheduleResource scheduleResource = new ScheduleResource(scheduleLogic);
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
		ScheduleLogic scheduleLogic = mock(ScheduleLogic.class);
		ScheduleResource scheduleResource = new ScheduleResource(scheduleLogic);
		ScheduleCdo scheduleCdo = new ScheduleCdo();
		ScheduleRdo scheduleRdo = new ScheduleRdo();
		when(scheduleLogic.registerSchedule(scheduleCdo)).thenReturn(scheduleRdo);

		ResponseEntity<ScheduleRdo> response = scheduleResource.registerSchedule(scheduleCdo);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(scheduleRdo, response.getBody());
	}

	@Test
	void modifySchedule_shouldReturnUpdatedSchedule() {
		ScheduleLogic scheduleLogic = mock(ScheduleLogic.class);
		ScheduleResource scheduleResource = new ScheduleResource(scheduleLogic);
		ScheduleUdo scheduleUdo = new ScheduleUdo();
		ScheduleRdo scheduleRdo = new ScheduleRdo();
		when(scheduleLogic.modifySchedule(scheduleUdo)).thenReturn(scheduleRdo);

		ResponseEntity<ScheduleRdo> response = scheduleResource.modifySchedule(scheduleUdo);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(scheduleRdo, response.getBody());
	}

	@Test
	void hideAndDelete_shouldReturnNoContent() {
		ScheduleLogic scheduleLogic = mock(ScheduleLogic.class);
		ScheduleResource scheduleResource = new ScheduleResource(scheduleLogic);

		ResponseEntity<Void> hideResponse = scheduleResource.hideSchedule("schedule-1");
		ResponseEntity<Void> deleteResponse = scheduleResource.deleteSchedule("schedule-1");

		assertEquals(HttpStatus.NO_CONTENT, hideResponse.getStatusCode());
		assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());
		verify(scheduleLogic).hideSchedule("schedule-1");
		verify(scheduleLogic).deleteSchedule("schedule-1");
	}
}
