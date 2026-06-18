package com.seoulchonnom.spec.trip.mapper;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.seoulchonnom.spec.trip.entity.Trip;
import com.seoulchonnom.spec.trip.entity.vo.Option;
import com.seoulchonnom.spec.trip.entity.vo.Quiz;
import com.seoulchonnom.spec.trip.facade.sdo.QuizRdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizResultRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripDetailRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripListRdo;

class TripMapperTest {
	private final TripMapper tripMapper = Mappers.getMapper(TripMapper.class);

	@Test
	void toTripListRdo_shouldMapTripSummary() {
		Trip trip = Trip.builder()
			.date("2026-03-31")
			.type("ryu")
			.name("Trip Name")
			.logo("logo.png")
			.quiz(Quiz.builder().title("Quiz Title").build())
			.build();
		trip.setId("trip-1");

		TripListRdo tripListRdo = tripMapper.toTripListRdo(trip);

		assertThat(tripListRdo.getId()).isEqualTo("trip-1");
		assertThat(tripListRdo.getType()).isEqualTo("ryu");
		assertThat(tripListRdo.getName()).isEqualTo("Trip Name");
	}

	@Test
	void toTripDetailRdo_shouldMapTripFields() {
		Trip trip = Trip.builder()
			.date("2026-03-31")
			.type("ayo")
			.name("Trip Name")
			.logo("logo.png")
			.firstMap("first")
			.secondMap("second")
			.nextButtonText("next")
			.previousButtonText("prev")
			.driveUrl("https://drive.example")
			.quiz(Quiz.builder()
				.title("Quiz Title")
				.correctOptionId("OPT-2")
				.answerTitle("Answer Title")
				.answerText("Answer Text")
				.errorTitle("Error Title")
				.errorText("Error Text")
				.options(List.of(
					Option.builder().id("OPT-1").text("wrong").build(),
					Option.builder().id("OPT-2").text("right").build()))
				.build())
			.build();
		trip.setId("trip-1");

		TripDetailRdo tripDetailRdo = tripMapper.toTripDetailRdo(trip);

		assertThat(tripDetailRdo.getDriveUrl()).isEqualTo("https://drive.example");
		assertThat(tripDetailRdo.getPreviousButtonText()).isEqualTo("prev");
	}

	@Test
	void toQuizRdo_shouldPreserveOptionOrder() {
		Quiz quiz = Quiz.builder()
			.title("Quiz Title")
			.options(List.of(
				Option.builder().id("OPT-1").text("first").build(),
				Option.builder().id("OPT-2").text("second").build()))
			.build();

		QuizRdo quizRdo = tripMapper.toQuizRdo(quiz);

		assertThat(quizRdo.getTitle()).isEqualTo("Quiz Title");
		assertThat(quizRdo.getOptions())
			.extracting(option -> option.getId() + ":" + option.getText())
			.containsExactly("OPT-1:first", "OPT-2:second");
	}

	@Test
	void toQuizDetailRdo_shouldReturnAnswerPayloadForCorrectOption() {
		Quiz quiz = Quiz.builder()
			.correctOptionId("OPT-2")
			.answerTitle("정답")
			.answerText("정답 설명")
			.errorTitle("오답")
			.errorText("오답 설명")
			.build();

		QuizResultRdo quizResultRdo = tripMapper.toQuizDetailRdo(quiz, "OPT-2");

		assertThat(quizResultRdo.isCorrect()).isTrue();
		assertThat(quizResultRdo.getTitle()).isEqualTo("정답");
		assertThat(quizResultRdo.getText()).isEqualTo("정답 설명");
	}

	@Test
	void toQuizDetailRdo_shouldReturnErrorPayloadForWrongOption() {
		Quiz quiz = Quiz.builder()
			.correctOptionId("OPT-2")
			.answerTitle("정답")
			.answerText("정답 설명")
			.errorTitle("오답")
			.errorText("오답 설명")
			.build();

		QuizResultRdo quizResultRdo = tripMapper.toQuizDetailRdo(quiz, "OPT-1");

		assertThat(quizResultRdo.isCorrect()).isFalse();
		assertThat(quizResultRdo.getTitle()).isEqualTo("오답");
		assertThat(quizResultRdo.getText()).isEqualTo("오답 설명");
	}
}
