package com.seoulchonnom.aggregate.calendar.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.aggregate.calendar.store.jpo.CalendarJpo;
import com.seoulchonnom.spec.calendar.entity.Calendar;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true))
public interface CalendarJpoMapper {
	CalendarJpo toJpo(Calendar calendar);

	Calendar toDomain(CalendarJpo calendarJpo);

	@AfterMapping
	default void mapInheritedFields(CalendarJpo calendarJpo, @MappingTarget Calendar calendar) {
		calendar.setId(calendarJpo.getId());
		calendar.setEntityVersion(calendarJpo.getEntityVersion());
		if (calendarJpo.getRegisteredTime() != null) {
			calendar.setRegisteredTime(calendarJpo.getRegisteredTime());
		}
		if (calendarJpo.getModifiedTime() != null) {
			calendar.setModifiedTime(calendarJpo.getModifiedTime());
		}
	}
}
