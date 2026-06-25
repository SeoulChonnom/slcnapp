package com.seoulchonnom.aggregate.trip.store.mapper;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.seoulchonnom.aggregate.trip.store.jpo.TripJpo;
import com.seoulchonnom.spec.trip.entity.Trip;
import com.seoulchonnom.spec.trip.entity.vo.Option;
import com.seoulchonnom.spec.trip.entity.vo.Quiz;

class TripJpoMapperTest {
	private final TripJpoMapper tripJpoMapper = Mappers.getMapper(TripJpoMapper.class);

	@Test
	void toDomain_shouldPreserveInheritedAndQuizFields() {
		TripJpo tripJpo = new TripJpo();
		tripJpo.setId("TRIP-1");
		tripJpo.setEntityVersion(7L);
		tripJpo.setRegisteredTime(100L);
		tripJpo.setModifiedTime(200L);
		tripJpo.setDate("2026-03-31");
		tripJpo.setType("ryu");
		tripJpo.setName("Trip Name");
		tripJpo.setLogoFileId("logo-file-1");
		tripJpo.setFirstMapFileId("map-file-1");
		tripJpo.setSecondMapFileId("map-file-2");
		tripJpo.setNextButtonText("next");
		tripJpo.setPreviousButtonText("prev");
		tripJpo.setDriveUrl("https://drive.example");
		tripJpo.setQuiz(Quiz.builder()
			.title("Quiz Title")
			.correctOptionId("OPT-2")
			.answerTitle("Answer Title")
			.answerText("Answer Text")
			.errorTitle("Error Title")
			.errorText("Error Text")
			.options(List.of(
				Option.builder().id("OPT-1").text("wrong").build(),
				Option.builder().id("OPT-2").text("right").build()))
			.build());

		Trip trip = tripJpoMapper.toDomain(tripJpo);

		assertThat(trip.getId()).isEqualTo("TRIP-1");
		assertThat(trip.getEntityVersion()).isEqualTo(7L);
		assertThat(trip.getRegisteredTime()).isEqualTo(100L);
		assertThat(trip.getModifiedTime()).isEqualTo(200L);
		assertThat(trip.getDate()).isEqualTo("2026-03-31");
		assertThat(trip.getDriveUrl()).isEqualTo("https://drive.example");
		assertThat(trip.getLogoFileId()).isEqualTo("logo-file-1");
		assertThat(trip.getFirstMapFileId()).isEqualTo("map-file-1");
		assertThat(trip.getSecondMapFileId()).isEqualTo("map-file-2");
		assertThat(trip.getQuiz().getCorrectOptionId()).isEqualTo("OPT-2");
		assertThat(trip.getQuiz().getOptions()).extracting("id").containsExactly("OPT-1", "OPT-2");
	}

	@Test
	void toJpo_shouldMapQuizIntoCurrentJsonBackedShape() {
		Trip trip = Trip.builder()
			.date("2026-03-31")
			.type("ayo")
			.name("Trip Name")
			.logoFileId("logo-file-1")
			.firstMapFileId("map-file-1")
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
		trip.setId("TRIP-9");

		TripJpo tripJpo = tripJpoMapper.toJpo(trip);

		assertThat(tripJpo.getId()).isEqualTo("TRIP-9");
		assertThat(tripJpo.getLogoFileId()).isEqualTo("logo-file-1");
		assertThat(tripJpo.getFirstMapFileId()).isEqualTo("map-file-1");
		assertThat(tripJpo.getQuiz()).isNotNull();
		assertThat(tripJpo.getQuiz().getCorrectOptionId()).isEqualTo("OPT-2");
		assertThat(tripJpo.getQuiz().getOptions()).extracting("text").containsExactly("wrong", "right");
	}
}
