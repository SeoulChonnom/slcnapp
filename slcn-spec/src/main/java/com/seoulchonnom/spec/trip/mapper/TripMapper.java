package com.seoulchonnom.spec.trip.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.seoulchonnom.spec.trip.entity.Quiz;
import com.seoulchonnom.spec.trip.entity.Trip;
import com.seoulchonnom.spec.trip.facade.sdo.QuizRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripInfoRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripListRdo;

@Mapper(componentModel = SPRING)
public interface TripMapper {
	TripListRdo toTripListRdo(Trip trip);

	@Mapping(target = "drive", source = "driveUrl")
	TripInfoRdo toTripInfoRdo(Trip trip);

	QuizRdo toQuizRdo(Quiz quiz);

	default List<QuizRdo> map(List<Quiz> quizList) {
		if (quizList == null) {
			return List.of();
		}

		return quizList.stream()
			.map(this::toQuizRdo)
			.toList();
	}
}
