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
		Option option1 = new Option();
		option1.setId("option-1");
		option1.setText("wrong");
		option1.setSortOrder(2);

		Option option2 = new Option();
		option2.setId("option-2");
		option2.setText("right");
		option2.setSortOrder(1);

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
				.correctOptionId("option-2")
				.answerTitle("Answer Title")
				.answerText("Answer Text")
				.errorTitle("Error Title")
				.errorText("Error Text")
				.options(List.of(option1, option2))
				.build())
			.build();
		trip.setId("trip-1");

		TripDetailRdo tripDetailRdo = tripMapper.toTripDetailRdo(trip);

		assertThat(tripDetailRdo.getDriveUrl()).isEqualTo("https://drive.example");
	}
}
