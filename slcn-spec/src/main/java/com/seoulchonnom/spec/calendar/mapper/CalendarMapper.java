package com.seoulchonnom.spec.calendar.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.Mapper;

import com.seoulchonnom.spec.calendar.entity.Calendar;
import com.seoulchonnom.spec.calendar.facade.sdo.CalendarRdo;

@Mapper(componentModel = SPRING)
public interface CalendarMapper {
	CalendarRdo toCalendarRdo(Calendar calendar);
}
