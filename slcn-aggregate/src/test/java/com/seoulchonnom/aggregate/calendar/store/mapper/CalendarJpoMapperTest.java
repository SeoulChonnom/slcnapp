package com.seoulchonnom.aggregate.calendar.store.mapper;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.seoulchonnom.aggregate.calendar.store.jpo.CalendarJpo;
import com.seoulchonnom.spec.calendar.entity.Calendar;

@SpringJUnitConfig
@ContextConfiguration(classes = CalendarJpoMapper.class)
class CalendarJpoMapperTest {
	@Autowired
	private CalendarJpoMapper calendarJpoMapper;

	@Test
	void toDomain_shouldPreserveManagedFields() {
		CalendarJpo calendarJpo = new CalendarJpo();
		calendarJpo.setId("CALENDAR-0001");
		calendarJpo.setEntityVersion(5L);
		calendarJpo.setRegisteredTime(100L);
		calendarJpo.setModifiedTime(200L);
		calendarJpo.setName("아영");
		calendarJpo.setBackgroundColor("#FE9FC8");
		calendarJpo.setBorderColor("#FE9FC8");
		calendarJpo.setTextColor("#111111");
		calendarJpo.setVisible(true);
		calendarJpo.setEditable(true);
		calendarJpo.setStartEditable(true);
		calendarJpo.setDurationEditable(true);
		calendarJpo.setDefaultSelected(true);
		calendarJpo.setSortOrder(1);

		Calendar calendar = calendarJpoMapper.toDomain(calendarJpo);

		assertThat(calendar.getId()).isEqualTo("CALENDAR-0001");
		assertThat(calendar.getEntityVersion()).isEqualTo(5L);
		assertThat(calendar.getRegisteredTime()).isEqualTo(100L);
		assertThat(calendar.getModifiedTime()).isEqualTo(200L);
		assertThat(calendar.getName()).isEqualTo("아영");
		assertThat(calendar.getBackgroundColor()).isEqualTo("#FE9FC8");
		assertThat(calendar.getBorderColor()).isEqualTo("#FE9FC8");
		assertThat(calendar.getTextColor()).isEqualTo("#111111");
		assertThat(calendar.isVisible()).isTrue();
		assertThat(calendar.isEditable()).isTrue();
		assertThat(calendar.isStartEditable()).isTrue();
		assertThat(calendar.isDurationEditable()).isTrue();
		assertThat(calendar.isDefaultSelected()).isTrue();
		assertThat(calendar.getSortOrder()).isEqualTo(1);
	}

	@Test
	void toJpo_shouldPreserveManagedFields() {
		Calendar calendar = Calendar.builder()
			.name("아영")
			.backgroundColor("#FE9FC8")
			.borderColor("#FE9FC8")
			.textColor("#111111")
			.visible(true)
			.editable(true)
			.startEditable(true)
			.durationEditable(true)
			.defaultSelected(true)
			.sortOrder(1)
			.build();
		calendar.setId("CALENDAR-0002");
		calendar.setEntityVersion(7L);
		calendar.setRegisteredTime(300L);
		calendar.setModifiedTime(400L);

		CalendarJpo calendarJpo = calendarJpoMapper.toJpo(calendar);

		assertThat(calendarJpo.getId()).isEqualTo("CALENDAR-0002");
		assertThat(calendarJpo.getEntityVersion()).isEqualTo(7L);
		assertThat(calendarJpo.getRegisteredTime()).isEqualTo(300L);
		assertThat(calendarJpo.getModifiedTime()).isEqualTo(400L);
		assertThat(calendarJpo.getName()).isEqualTo("아영");
		assertThat(calendarJpo.getBackgroundColor()).isEqualTo("#FE9FC8");
		assertThat(calendarJpo.getBorderColor()).isEqualTo("#FE9FC8");
		assertThat(calendarJpo.getTextColor()).isEqualTo("#111111");
		assertThat(calendarJpo.isVisible()).isTrue();
		assertThat(calendarJpo.isEditable()).isTrue();
		assertThat(calendarJpo.isStartEditable()).isTrue();
		assertThat(calendarJpo.isDurationEditable()).isTrue();
		assertThat(calendarJpo.isDefaultSelected()).isTrue();
		assertThat(calendarJpo.getSortOrder()).isEqualTo(1);
	}
}
