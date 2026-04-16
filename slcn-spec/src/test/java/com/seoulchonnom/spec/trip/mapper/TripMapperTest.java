package com.seoulchonnom.spec.trip.mapper;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.seoulchonnom.spec.trip.entity.Quiz;
import com.seoulchonnom.spec.trip.entity.Trip;
import com.seoulchonnom.spec.trip.facade.sdo.TripInfoRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripListRdo;

class TripMapperTest {
	private final TripMapper tripMapper = Mappers.getMapper(TripMapper.class);

	@Test
	void toTripListRdo_shouldMapQuizList() {
		Trip trip = Trip.builder()
			.date("2026-03-31")
			.name("Trip Name")
			.logo("logo.png")
			.quizTitle("Quiz Title")
			.quizAnswer("A")
			.quizAnswerTitle("Answer Title")
			.quizAnswerText("Answer Text")
			.quizErrorTitle("Error Title")
			.quizErrorText("Error Text")
			.quizList(List.of(Quiz.builder().quizIndex("1").answer("answer").build()))
			.build();
		trip.setId("trip-1");

		TripListRdo tripListRdo = tripMapper.toTripListRdo(trip);

		assertThat(tripListRdo.getId()).isEqualTo("trip-1");
		assertThat(tripListRdo.getQuizList()).hasSize(1);
		assertThat(tripListRdo.getQuizList().get(0).getQuizIndex()).isEqualTo("1");
		assertThat(tripListRdo.getQuizList().get(0).getAnswer()).isEqualTo("answer");
	}

	@Test
	void toTripInfoRdo_shouldMapDriveFromDriveUrl() {
		Trip trip = Trip.builder()
			.date("2026-03-31")
			.firstMap("first")
			.secondMap("second")
			.nextButtonText("next")
			.previousButtonText("prev")
			.driveUrl("https://drive.example")
			.build();

		TripInfoRdo tripInfoRdo = tripMapper.toTripInfoRdo(trip);

		assertThat(tripInfoRdo.getDrive()).isEqualTo("https://drive.example");
		assertThat(tripInfoRdo.getFirstMap()).isEqualTo("first");
	}
}
