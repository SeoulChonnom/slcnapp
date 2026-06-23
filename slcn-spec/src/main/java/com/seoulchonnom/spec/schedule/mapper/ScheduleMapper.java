package com.seoulchonnom.spec.schedule.mapper;

import static com.seoulchonnom.spec.schedule.constant.ScheduleConstant.*;
import static org.mapstruct.MappingConstants.ComponentModel.*;

import java.time.LocalDateTime;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleCdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleRdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleUdo;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true))
public interface ScheduleMapper {
	@Mapping(target = "start", expression = "java(formatDateTime(schedule.getStart(), schedule.isAllDay()))")
	@Mapping(target = "end", expression = "java(formatDateTime(schedule.getEnd(), schedule.isAllDay()))")
	ScheduleRdo toScheduleRdo(Schedule schedule);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "entityVersion", ignore = true)
	@Mapping(target = "registeredTime", ignore = true)
	@Mapping(target = "modifiedTime", ignore = true)
	@Mapping(target = "start", expression = "java(parseDateTime(scheduleCdo.getStart(), scheduleCdo.isAllDay()))")
	@Mapping(target = "end", expression = "java(parseDateTime(scheduleCdo.getEnd(), scheduleCdo.isAllDay()))")
	@Mapping(target = "hidden", constant = "false")
	Schedule toSchedule(ScheduleCdo scheduleCdo);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "entityVersion", ignore = true)
	@Mapping(target = "registeredTime", ignore = true)
	@Mapping(target = "modifiedTime", ignore = true)
	@Mapping(target = "hidden", ignore = true)
	@Mapping(target = "start", expression = "java(parseDateTime(scheduleUdo.getStart(), scheduleUdo.isAllDay()))")
	@Mapping(target = "end", expression = "java(parseDateTime(scheduleUdo.getEnd(), scheduleUdo.isAllDay()))")
	void updateSchedule(ScheduleUdo scheduleUdo, @MappingTarget Schedule schedule);

	default String formatDateTime(LocalDateTime dateTime, boolean isAllDay) {
		if (dateTime == null) {
			return null;
		}

		return formatScheduleDateTime(dateTime, isAllDay);
	}

	default LocalDateTime parseDateTime(String dateTime, boolean isAllDay) {
		return parseMutationDateTime(dateTime, isAllDay);
	}
}
