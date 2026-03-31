package com.seoulchonnom.aggregate.schedule.store.mapper;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.seoulchonnom.aggregate.schedule.store.jpo.ScheduleJpo;
import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.entity.ScheduleCategory;
import com.seoulchonnom.spec.schedule.entity.ScheduleState;

@SpringJUnitConfig
@ContextConfiguration(classes = ScheduleJpoMapperImpl.class)
class ScheduleJpoMapperTest {

	@Autowired
	private ScheduleJpoMapper scheduleJpoMapper;

	@Test
	void toDomain_shouldPreserveAllFields() {
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
		scheduleJpo.setGoingDuration(10L);
		scheduleJpo.setComingDuration(20L);
		scheduleJpo.setLocation("Seoul");
		scheduleJpo.setCategory(ScheduleCategory.time);
		scheduleJpo.setDueDateClass("due");
		scheduleJpo.setRecurrenceRule("FREQ=DAILY");
		scheduleJpo.setState(ScheduleState.Busy);
		scheduleJpo.setVisible(true);
		scheduleJpo.setPending(false);
		scheduleJpo.setFocused(true);
		scheduleJpo.setReadOnly(false);
		scheduleJpo.setPrivate(false);
		scheduleJpo.setColor("#111");
		scheduleJpo.setBackgroundColor("#222");
		scheduleJpo.setDragBackgroundColor("#333");
		scheduleJpo.setBorderColor("#444");
		scheduleJpo.setCustomStyle("style");

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
		assertThat(schedule.getCategory()).isEqualTo(ScheduleCategory.time);
		assertThat(schedule.getState()).isEqualTo(ScheduleState.Busy);
		assertThat(schedule.isVisible()).isTrue();
		assertThat(schedule.getCustomStyle()).isEqualTo("style");
	}
}
