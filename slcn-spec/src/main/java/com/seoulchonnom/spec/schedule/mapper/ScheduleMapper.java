package com.seoulchonnom.spec.schedule.mapper;

import static com.seoulchonnom.spec.schedule.constant.ScheduleConstant.*;
import static org.mapstruct.MappingConstants.ComponentModel.*;

import java.time.LocalDateTime;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleRdo;

@Mapper(componentModel = SPRING)
public interface ScheduleMapper {
	@Mapping(target = "start", expression = "java(formatDateTime(schedule.getStart(), schedule.isAllDay()))")
	@Mapping(target = "end", expression = "java(formatDateTime(schedule.getEnd(), schedule.isAllDay()))")
	ScheduleRdo toScheduleRdo(Schedule schedule);

	default String formatDateTime(LocalDateTime dateTime, boolean isAllDay) {
		if (dateTime == null) {
			return null;
		}

		return formatScheduleDateTime(dateTime, isAllDay);
	}
}
