package com.seoulchonnom.aggregate.schedule.store.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.aggregate.schedule.store.jpo.ScheduleJpo;
import com.seoulchonnom.spec.schedule.entity.Schedule;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface ScheduleJpoMapper {
	ScheduleJpo toJpo(Schedule schedule);

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
