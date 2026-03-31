package com.seoulchonnom.aggregate.trip.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.aggregate.trip.store.jpo.TripJpo;
import com.seoulchonnom.spec.trip.entity.Trip;

@Mapper(componentModel = SPRING, uses = QuizJpoMapper.class, builder = @Builder(disableBuilder = true))
public interface TripJpoMapper {
	@Mapping(target = "quizList", source = "quizList")
	Trip toDomain(TripJpo tripJpo);

	@Mapping(target = "quizList", source = "quizList")
	TripJpo toJpo(Trip trip);

	@AfterMapping
	default void mapInheritedFields(TripJpo tripJpo, @MappingTarget Trip trip) {
		trip.setId(tripJpo.getId());
		trip.setEntityVersion(tripJpo.getEntityVersion());
		if (tripJpo.getRegisteredTime() != null) {
			trip.setRegisteredTime(tripJpo.getRegisteredTime());
		}
		if (tripJpo.getModifiedTime() != null) {
			trip.setModifiedTime(tripJpo.getModifiedTime());
		}
	}
}
