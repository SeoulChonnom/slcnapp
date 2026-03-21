package com.seoulchonnom.aggregate.schedule.store.mapper;

import org.mapstruct.Mapper;

import com.seoulchonnom.aggregate.schedule.store.jpo.ScheduleJpo;
import com.seoulchonnom.spec.schedule.entity.Schedule;

@Mapper(componentModel = "spring")
public interface ScheduleJpoMapper {
	ScheduleJpo toJpo(Schedule schedule);

	Schedule toDomain(ScheduleJpo scheduleJpo);
}
