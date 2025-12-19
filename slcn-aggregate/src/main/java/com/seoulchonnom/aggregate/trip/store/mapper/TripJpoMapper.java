package com.seoulchonnom.aggregate.trip.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.seoulchonnom.aggregate.trip.store.jpo.TripJpo;
import com.seoulchonnom.spec.trip.entity.Trip;

@Mapper(componentModel = SPRING, uses = QuizJpoMapper.class)
public interface TripJpoMapper {
	@Mapping(target = "quizList", source = "quizList")
	Trip toDomain(TripJpo tripJpo);

	@Mapping(target = "quizList", source = "quizList")
	TripJpo toJpo(Trip trip);
}
