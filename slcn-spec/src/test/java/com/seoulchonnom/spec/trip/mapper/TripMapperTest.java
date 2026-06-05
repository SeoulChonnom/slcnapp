package com.seoulchonnom.spec.trip.mapper;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.seoulchonnom.spec.trip.entity.Trip;
import com.seoulchonnom.spec.trip.entity.vo.Option;
import com.seoulchonnom.spec.trip.entity.vo.Quiz;
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
	void toTripDetailRdo_shouldMapQuizAndCorrectOption() {
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
				.tripId("trip-1")
				.title("Quiz Title")
				.correctOptionId("option-2")
				.answerTitle("Answer Title")
				.answerText("Answer Text")
				.errorTitle("Error Title")
				.errorText("Error Text")
				.quizOptions(List.of(
					Option.builder().id("option-1").tripId("trip-1").text("wrong").sortOrder(2).build(),
					Option.builder().id("option-2").tripId("trip-1").text("right").sortOrder(1).build()))
				.build())
			.build();
		trip.setId("trip-1");

		TripDetailRdo tripDetailRdo = tripMapper.toTripDetailRdo(trip);

		assertThat(tripDetailRdo.getDriveUrl()).isEqualTo("https://drive.example");
	}
}
