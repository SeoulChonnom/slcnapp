package com.seoulchonnom.aggregate.calendar.store.mapper;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.calendar.store.jpo.CalendarJpo;
import com.seoulchonnom.spec.calendar.entity.Calendar;

@Component
public class CalendarJpoMapper {
	public CalendarJpo toJpo(Calendar calendar) {
		CalendarJpo calendarJpo = new CalendarJpo();
		calendarJpo.setId(calendar.getId());
		calendarJpo.setEntityVersion(calendar.getEntityVersion());
		calendarJpo.setRegisteredTime(calendar.getRegisteredTime());
		calendarJpo.setModifiedTime(calendar.getModifiedTime());
		calendarJpo.setName(calendar.getName());
		calendarJpo.setBackgroundColor(calendar.getBackgroundColor());
		calendarJpo.setBorderColor(calendar.getBorderColor());
		calendarJpo.setTextColor(calendar.getTextColor());
		calendarJpo.setVisible(calendar.isVisible());
		calendarJpo.setEditable(calendar.isEditable());
		calendarJpo.setStartEditable(calendar.isStartEditable());
		calendarJpo.setDurationEditable(calendar.isDurationEditable());
		calendarJpo.setDefaultSelected(calendar.isDefaultSelected());
		calendarJpo.setSortOrder(calendar.getSortOrder());
		return calendarJpo;
	}

	public Calendar toDomain(CalendarJpo calendarJpo) {
		Calendar calendar = Calendar.builder()
			.name(calendarJpo.getName())
			.backgroundColor(calendarJpo.getBackgroundColor())
			.borderColor(calendarJpo.getBorderColor())
			.textColor(calendarJpo.getTextColor())
			.visible(calendarJpo.isVisible())
			.editable(calendarJpo.isEditable())
			.startEditable(calendarJpo.isStartEditable())
			.durationEditable(calendarJpo.isDurationEditable())
			.defaultSelected(calendarJpo.isDefaultSelected())
			.sortOrder(calendarJpo.getSortOrder())
			.build();
		calendar.setId(calendarJpo.getId());
		calendar.setEntityVersion(calendarJpo.getEntityVersion());
		if (calendarJpo.getRegisteredTime() != null) {
			calendar.setRegisteredTime(calendarJpo.getRegisteredTime());
		}
		if (calendarJpo.getModifiedTime() != null) {
			calendar.setModifiedTime(calendarJpo.getModifiedTime());
		}
		return calendar;
	}
}
