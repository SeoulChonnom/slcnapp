package com.seoulchonnom.spec.calendar.mapper;

import org.springframework.stereotype.Component;

import com.seoulchonnom.spec.calendar.entity.Calendar;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarRdo;

@Component
public class CalendarMapper {
	public CalendarRdo toCalendarRdo(Calendar calendar) {
		CalendarRdo calendarRdo = new CalendarRdo();
		calendarRdo.setId(calendar.getId());
		calendarRdo.setName(calendar.getName());
		calendarRdo.setBackgroundColor(calendar.getBackgroundColor());
		calendarRdo.setBorderColor(calendar.getBorderColor());
		calendarRdo.setTextColor(calendar.getTextColor());
		calendarRdo.setVisible(calendar.isVisible());
		calendarRdo.setEditable(calendar.isEditable());
		calendarRdo.setStartEditable(calendar.isStartEditable());
		calendarRdo.setDurationEditable(calendar.isDurationEditable());
		calendarRdo.setDefaultSelected(calendar.isDefaultSelected());
		calendarRdo.setSortOrder(calendar.getSortOrder());
		return calendarRdo;
	}
}
