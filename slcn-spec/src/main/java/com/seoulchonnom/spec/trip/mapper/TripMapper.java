package com.seoulchonnom.spec.trip.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.seoulchonnom.spec.trip.entity.Trip;
import com.seoulchonnom.spec.trip.entity.vo.Option;
import com.seoulchonnom.spec.trip.entity.vo.Quiz;
import com.seoulchonnom.spec.trip.facade.sdo.OptionRdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizRdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizResultRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripDetailRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripListRdo;

@Mapper(componentModel = SPRING)
public interface TripMapper {
	TripListRdo toTripListRdo(Trip trip);

	TripDetailRdo toTripDetailRdo(Trip trip);

	OptionRdo toOptionRdo(Option option);

	@Mapping(target = "options", expression = "java(toOptionRdoList(quiz.getOptions()))")
	QuizRdo toQuizRdo(Quiz quiz);

	default List<OptionRdo> toOptionRdoList(List<Option> options) {
		return options.stream()
			.map(this::toOptionRdo)
			.toList();
	}

	default QuizResultRdo toQuizDetailRdo(Quiz quiz, String optionId) {
		QuizResultRdo quizResultRdo = new QuizResultRdo();

		if (quiz.getCorrectOptionId().equals(optionId)) {
			quizResultRdo.setCorrect(true);
			quizResultRdo.setTitle(quiz.getAnswerTitle());
			quizResultRdo.setText(quiz.getAnswerText());
		} else {
			quizResultRdo.setCorrect(false);
			quizResultRdo.setTitle(quiz.getErrorTitle());
			quizResultRdo.setText(quiz.getErrorText());
		}
		return quizResultRdo;
	}
}
