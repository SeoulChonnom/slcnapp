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
		tripJpo.setDriveUrl("https://drive.example");
		tripJpo.setQuiz(quiz());

		Trip trip = tripJpoMapper.toDomain(tripJpo);

		assertThat(trip.getId()).isEqualTo("TRIP-1");
		assertThat(trip.getEntityVersion()).isEqualTo(7L);
		assertThat(trip.getRegisteredTime()).isEqualTo(100L);
		assertThat(trip.getModifiedTime()).isEqualTo(200L);
		assertThat(trip.getDate()).isEqualTo("2026-03-31");
		assertThat(trip.getDriveUrl()).isEqualTo("https://drive.example");
		assertThat(trip.getQuiz().getCorrectOptionId()).isEqualTo("OPT-2");
	}

	@Test
	void toJpo_shouldMapCurrentTripRootShape() {
		Trip trip = Trip.builder()
			.date("2026-03-31")
			.type("ayo")
			.name("Trip Name")
			.driveUrl("https://drive.example")
			.quiz(quiz())
			.build();
		trip.setId("TRIP-9");

		TripJpo tripJpo = tripJpoMapper.toJpo(trip);

		assertThat(tripJpo.getId()).isEqualTo("TRIP-9");
		assertThat(tripJpo.getQuiz().getCorrectOptionId()).isEqualTo("OPT-2");
		assertThat(tripJpo.getQuiz().getOptions()).extracting("text").containsExactly("wrong", "right");
	}

	private Quiz quiz() {
		return Quiz.builder()
			.title("Quiz Title")
			.correctOptionId("OPT-2")
			.answerTitle("Answer Title")
			.answerText("Answer Text")
			.errorTitle("Error Title")
			.errorText("Error Text")
			.options(List.of(
				Option.builder().id("OPT-1").text("wrong").build(),
				Option.builder().id("OPT-2").text("right").build()))
			.build();
	}
}
