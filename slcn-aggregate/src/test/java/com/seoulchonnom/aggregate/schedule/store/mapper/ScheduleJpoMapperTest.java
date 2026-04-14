package com.seoulchonnom.aggregate.schedule.store.mapper;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.seoulchonnom.aggregate.schedule.store.jpo.ScheduleJpo;
import com.seoulchonnom.spec.schedule.entity.Schedule;

@SpringJUnitConfig
@ContextConfiguration(classes = ScheduleJpoMapper.class)
class ScheduleJpoMapperTest {

	@Autowired
	private ScheduleJpoMapper scheduleJpoMapper;

	@Test
	void toDomain_shouldPreserveManagedFields() {
		LocalDateTime start = LocalDateTime.of(2026, 3, 31, 9, 0);
		LocalDateTime end = LocalDateTime.of(2026, 3, 31, 10, 0);

		ScheduleJpo scheduleJpo = new ScheduleJpo();
		scheduleJpo.setId("SCHEDULE-1");
		scheduleJpo.setEntityVersion(5L);
		scheduleJpo.setRegisteredTime(100L);
		scheduleJpo.setModifiedTime(200L);
		scheduleJpo.setCalendarId("cal-1");
		scheduleJpo.setTitle("Title");
		scheduleJpo.setBody("Body");
		scheduleJpo.setAllDay(true);
		scheduleJpo.setStart(start);
		scheduleJpo.setEnd(end);
		scheduleJpo.setLocation("Seoul");
		scheduleJpo.setHidden(true);

		Schedule schedule = scheduleJpoMapper.toDomain(scheduleJpo);

		assertThat(schedule.getId()).isEqualTo("SCHEDULE-1");
		assertThat(schedule.getEntityVersion()).isEqualTo(5L);
		assertThat(schedule.getRegisteredTime()).isEqualTo(100L);
		assertThat(schedule.getModifiedTime()).isEqualTo(200L);
		assertThat(schedule.getCalendarId()).isEqualTo("cal-1");
		assertThat(schedule.getTitle()).isEqualTo("Title");
		assertThat(schedule.isAllDay()).isTrue();
		assertThat(schedule.getStart()).isEqualTo(start);
		assertThat(schedule.getEnd()).isEqualTo(end);
		assertThat(schedule.getLocation()).isEqualTo("Seoul");
		assertThat(schedule.isHidden()).isTrue();
	}

	@Test
	void toJpo_shouldPreserveManagedFields() {
		LocalDateTime start = LocalDateTime.of(2026, 4, 2, 9, 0);
		LocalDateTime end = LocalDateTime.of(2026, 4, 2, 10, 0);

		Schedule schedule = Schedule.builder()
			.calendarId("cal-2")
			.title("Title")
			.body("Body")
			.allDay(false)
			.start(start)
			.end(end)
			.location("Busan")
			.hidden(false)
			.build();
		schedule.setId("SCHEDULE-2");
		schedule.setEntityVersion(7L);
		schedule.setRegisteredTime(300L);
		schedule.setModifiedTime(400L);

		ScheduleJpo scheduleJpo = scheduleJpoMapper.toJpo(schedule);

		assertThat(scheduleJpo.getId()).isEqualTo("SCHEDULE-2");
		assertThat(scheduleJpo.getEntityVersion()).isEqualTo(7L);
		assertThat(scheduleJpo.getRegisteredTime()).isEqualTo(300L);
		assertThat(scheduleJpo.getModifiedTime()).isEqualTo(400L);
		assertThat(scheduleJpo.getCalendarId()).isEqualTo("cal-2");
		assertThat(scheduleJpo.getTitle()).isEqualTo("Title");
		assertThat(scheduleJpo.getBody()).isEqualTo("Body");
		assertThat(scheduleJpo.isAllDay()).isFalse();
		assertThat(scheduleJpo.getStart()).isEqualTo(start);
		assertThat(scheduleJpo.getEnd()).isEqualTo(end);
		assertThat(scheduleJpo.getLocation()).isEqualTo("Busan");
		assertThat(scheduleJpo.isHidden()).isFalse();
	}
}
