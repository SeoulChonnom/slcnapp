package com.seoulchonnom.aggregate.trip.store.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.aggregate.trip.store.jpo.TripQuizJpo;
import com.seoulchonnom.aggregate.trip.store.jpo.TripQuizOptionJpo;
import com.seoulchonnom.spec.trip.entity.vo.Quiz;

@Mapper(componentModel = SPRING, uses = TripQuizOptionJpoMapper.class, builder = @Builder(disableBuilder = true))
public interface TripQuizJpoMapper {
	Quiz toDomain(TripQuizJpo tripQuizJpo);

	@Mapping(target = "trip", ignore = true)
	TripQuizJpo toJpo(Quiz quiz);

	@AfterMapping
	default void linkOptions(@MappingTarget TripQuizJpo tripQuizJpo) {
		if (tripQuizJpo.getOptions() == null) {
			return;
		}

		for (TripQuizOptionJpo option : tripQuizJpo.getOptions()) {
			option.setQuiz(tripQuizJpo);
		}
	}
}
