package com.seoulchonnom.aggregate.schedule.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.aggregate.schedule.store.jpo.ScheduleJpo;
import com.seoulchonnom.spec.schedule.entity.Schedule;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true))
public interface ScheduleJpoMapper {
	@Mapping(target = "allDay", source = "allDay")
	ScheduleJpo toJpo(Schedule schedule);

	@Mapping(target = "allDay", source = "allDay")
	Schedule toDomain(ScheduleJpo scheduleJpo);

	@AfterMapping
	default void mapInheritedFields(ScheduleJpo scheduleJpo, @MappingTarget Schedule schedule) {
		schedule.setId(scheduleJpo.getId());
		schedule.setEntityVersion(scheduleJpo.getEntityVersion());
		if (scheduleJpo.getRegisteredTime() != null) {
			schedule.setRegisteredTime(scheduleJpo.getRegisteredTime());
		}
		if (scheduleJpo.getModifiedTime() != null) {
			schedule.setModifiedTime(scheduleJpo.getModifiedTime());
		}
	}
}
