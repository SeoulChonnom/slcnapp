package com.seoulchonnom.spec.trip.mapper;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.seoulchonnom.spec.file.entity.vo.FileReference;
import com.seoulchonnom.spec.file.entity.vo.FileType;
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
			.logo(new FileReference(FileType.LOGO, "72d768d4-2b05-48f9-bee8-fee3b52e909f.png"))
			.quiz(Quiz.builder().title("Quiz Title").build())
			.build();
		trip.setId("trip-1");

		TripListRdo tripListRdo = tripMapper.toTripListRdo(trip);

		assertThat(tripListRdo.getId()).isEqualTo("trip-1");
		assertThat(tripListRdo.getType()).isEqualTo("ryu");
		assertThat(tripListRdo.getName()).isEqualTo("Trip Name");
		assertThat(tripListRdo.getLogo().getType()).isEqualTo(FileType.LOGO);
		assertThat(tripListRdo.getLogo().getFilename()).isEqualTo("72d768d4-2b05-48f9-bee8-fee3b52e909f.png");
	}

	@Test
	void toTripDetailRdo_shouldMapTripFields() {
		Trip trip = Trip.builder()
			.date("2026-03-31")
			.type("ayo")
			.name("Trip Name")
			.logo(new FileReference(FileType.LOGO, "72d768d4-2b05-48f9-bee8-fee3b52e909f.png"))
			.firstMap(new FileReference(FileType.MAP, "11111111-2222-4333-8888-aaaaaaaaaaaa.png"))
			.secondMap(new FileReference(FileType.MAP, "22222222-3333-4444-9999-bbbbbbbbbbbb.png"))
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
		assertThat(tripDetailRdo.getLogo().getType()).isEqualTo(FileType.LOGO);
		assertThat(tripDetailRdo.getLogo().getFilename()).isEqualTo("72d768d4-2b05-48f9-bee8-fee3b52e909f.png");
		assertThat(tripDetailRdo.getFirstMap().getType()).isEqualTo(FileType.MAP);
		assertThat(tripDetailRdo.getFirstMap().getFilename()).isEqualTo("11111111-2222-4333-8888-aaaaaaaaaaaa.png");
		assertThat(tripDetailRdo.getSecondMap().getType()).isEqualTo(FileType.MAP);
		assertThat(tripDetailRdo.getSecondMap().getFilename()).isEqualTo("22222222-3333-4444-9999-bbbbbbbbbbbb.png");
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
