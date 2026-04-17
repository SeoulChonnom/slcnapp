package com.seoulchonnom.spec.trip.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import java.util.Comparator;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.seoulchonnom.spec.trip.entity.Trip;
import com.seoulchonnom.spec.trip.entity.TripQuiz;
import com.seoulchonnom.spec.trip.entity.TripQuizOption;
import com.seoulchonnom.spec.trip.facade.sdo.TripDetailRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripListRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripQuizDetailRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripQuizOptionRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripQuizRdo;

@Mapper(componentModel = SPRING)
public interface TripMapper {
	TripListRdo toTripListRdo(Trip trip);

	TripDetailRdo toTripDetailRdo(Trip trip);

	@Mapping(target = "options", expression = "java(toTripQuizOptionRdoList(tripQuiz.getOptions()))")
	TripQuizRdo toTripQuizRdo(TripQuiz tripQuiz);

	default List<TripQuizOptionRdo> toTripQuizOptionRdoList(List<TripQuizOption> options) {
		return options.stream()
			.sorted(Comparator.comparingInt(TripQuizOption::getSortOrder))
			.map(this::toTripQuizOptionRdo)
			.toList();
	}

	default TripQuizOptionRdo toTripQuizOptionRdo(TripQuizOption option) {
		TripQuizOptionRdo tripQuizOptionRdo = new TripQuizOptionRdo();
		tripQuizOptionRdo.setId(option.getId());
		tripQuizOptionRdo.setText(option.getText());
		tripQuizOptionRdo.setSortOrder(option.getSortOrder());
		return tripQuizOptionRdo;
	}

	default TripQuizDetailRdo toTripQuizDetailRdo(TripQuiz tripQuiz, String optionId) {
		TripQuizDetailRdo tripQuizDetailRdo = new TripQuizDetailRdo();

		if (tripQuiz.getCorrectOptionId().equals(optionId)) {
			tripQuizDetailRdo.setCorrect(true);
			tripQuizDetailRdo.setTitle(tripQuiz.getAnswerTitle());
			tripQuizDetailRdo.setText(tripQuiz.getAnswerText());
		} else {
			tripQuizDetailRdo.setCorrect(false);
			tripQuizDetailRdo.setTitle(tripQuiz.getErrorTitle());
			tripQuizDetailRdo.setText(tripQuiz.getErrorText());
		}
		return tripQuizDetailRdo;
	}
}
