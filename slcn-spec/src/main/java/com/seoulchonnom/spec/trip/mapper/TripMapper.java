package com.seoulchonnom.spec.trip.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import java.util.List;
import java.util.stream.IntStream;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;
import com.seoulchonnom.spec.trip.entity.Trip;
import com.seoulchonnom.spec.trip.entity.vo.Option;
import com.seoulchonnom.spec.trip.entity.vo.Quiz;
import com.seoulchonnom.spec.trip.facade.sdo.OptionCdo;
import com.seoulchonnom.spec.trip.facade.sdo.OptionRdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizCdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizRdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizResultRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripDetailRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripListRdo;

@Mapper(componentModel = SPRING)
public interface TripMapper {
	@Mapping(target = "id", source = "trip.id")
	@Mapping(target = "date", source = "trip.date")
	@Mapping(target = "type", source = "trip.type")
	@Mapping(target = "name", source = "trip.name")
	@Mapping(target = "logo", source = "logo")
	TripListRdo toTripListRdo(Trip trip, FileAssetRdo logo);

	@Mapping(target = "id", source = "trip.id")
	@Mapping(target = "date", source = "trip.date")
	@Mapping(target = "type", source = "trip.type")
	@Mapping(target = "name", source = "trip.name")
	@Mapping(target = "logo", source = "logo")
	@Mapping(target = "firstMap", source = "firstMap")
	@Mapping(target = "secondMap", source = "secondMap")
	@Mapping(target = "nextButtonText", source = "trip.nextButtonText")
	@Mapping(target = "previousButtonText", source = "trip.previousButtonText")
	@Mapping(target = "driveUrl", source = "trip.driveUrl")
	TripDetailRdo toTripDetailRdo(Trip trip, FileAssetRdo logo, FileAssetRdo firstMap, FileAssetRdo secondMap);

	OptionRdo toOptionRdo(Option option);

	@Mapping(target = "options", expression = "java(toOptionRdoList(quiz.getOptions()))")
	QuizRdo toQuizRdo(Quiz quiz);

	default List<OptionRdo> toOptionRdoList(List<Option> options) {
		return options.stream()
			.map(this::toOptionRdo)
			.toList();
	}

	default Quiz toQuiz(QuizCdo quizCdo) {
		List<OptionCdo> optionCdos = quizCdo.getOptions();
		List<Option> options = IntStream.range(0, optionCdos.size())
			.mapToObj(index -> toOption(optionCdos.get(index), index + 1))
			.toList();

		int correctOptionIndex = IntStream.range(0, optionCdos.size())
			.filter(index -> optionCdos.get(index).isCorrect())
			.findFirst()
			.orElse(-1);
		String correctOptionId = correctOptionIndex >= 0 ? options.get(correctOptionIndex).getId() : null;

		return new Quiz(
			quizCdo.getTitle(),
			correctOptionId,
			quizCdo.getAnswerTitle(),
			quizCdo.getAnswerText(),
			quizCdo.getErrorTitle(),
			quizCdo.getErrorText(),
			options
		);
	}

	private Option toOption(OptionCdo optionCdo, int index) {
		return new Option("OPT-" + index, optionCdo.getText());
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
