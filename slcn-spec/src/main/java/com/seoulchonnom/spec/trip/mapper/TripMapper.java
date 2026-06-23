package com.seoulchonnom.spec.trip.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.*;

import java.util.List;

import org.mapstruct.AfterMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.seoulchonnom.spec.file.entity.vo.FileReference;
import com.seoulchonnom.spec.file.facade.sdo.FileReferenceSdo;
import com.seoulchonnom.spec.trip.entity.Trip;
import com.seoulchonnom.spec.trip.entity.vo.Option;
import com.seoulchonnom.spec.trip.entity.vo.Quiz;
import com.seoulchonnom.spec.trip.facade.sdo.OptionCdo;
import com.seoulchonnom.spec.trip.facade.sdo.OptionRdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizCdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizRdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizResultRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripDetailRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripListRdo;

@Mapper(componentModel = SPRING, builder = @Builder(disableBuilder = true))
public interface TripMapper {
	TripListRdo toTripListRdo(Trip trip);

	TripDetailRdo toTripDetailRdo(Trip trip);

	OptionRdo toOptionRdo(Option option);

	@Mapping(target = "options", expression = "java(toOptionRdoList(quiz.getOptions()))")
	QuizRdo toQuizRdo(Quiz quiz);

	@Mapping(target = "id", source = "id")
	@Mapping(target = "entityVersion", ignore = true)
	@Mapping(target = "registeredTime", ignore = true)
	@Mapping(target = "modifiedTime", ignore = true)
	Trip toTrip(TripCdo tripCdo, String id);

	@Mapping(target = "correctOptionId", ignore = true)
	Quiz toQuiz(QuizCdo quizCdo);

	@Mapping(target = "id", ignore = true)
	Option toOption(OptionCdo optionCdo);

	default List<OptionRdo> toOptionRdoList(List<Option> options) {
		return options.stream()
			.map(this::toOptionRdo)
			.toList();
	}

	default FileReferenceSdo map(FileReference fileReference) {
		return FileReferenceSdo.from(fileReference);
	}

	default FileReference map(FileReferenceSdo fileReferenceSdo) {
		if (fileReferenceSdo == null) {
			return null;
		}
		return new FileReference(fileReferenceSdo.getType(), fileReferenceSdo.getFilename());
	}

	@AfterMapping
	default void mapOptionIds(QuizCdo quizCdo, @MappingTarget Quiz quiz) {
		List<Option> options = quiz.getOptions();
		if (options == null) {
			return;
		}

		for (int i = 0; i < options.size(); i++) {
			Option option = options.get(i);
			String optionId = "OPT-" + (i + 1);
			option.setId(optionId);
			if (quizCdo.getOptions().get(i).isCorrect()) {
				quiz.setCorrectOptionId(optionId);
			}
		}
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
