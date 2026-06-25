package com.seoulchonnom.spec.calendar.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.spec.calendar.entity.Calendar;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarCdo;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarRdo;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarUdo;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true))
public interface CalendarMapper {
	CalendarRdo toCalendarRdo(Calendar calendar);

	@Mapping(target = "id", source = "id")
	@Mapping(target = "visible", constant = "true")
	@Mapping(target = "entityVersion", ignore = true)
	@Mapping(target = "registeredTime", ignore = true)
	@Mapping(target = "modifiedTime", ignore = true)
	Calendar toCalendar(CalendarCdo calendarCdo, String id);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "entityVersion", ignore = true)
	@Mapping(target = "registeredTime", ignore = true)
	@Mapping(target = "modifiedTime", ignore = true)
	@Mapping(target = "visible", ignore = true)
	void updateCalendar(CalendarUdo calendarUdo, @MappingTarget Calendar calendar);
}
