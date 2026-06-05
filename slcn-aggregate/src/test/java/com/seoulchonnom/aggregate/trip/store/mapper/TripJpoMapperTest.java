package com.seoulchonnom.aggregate.trip.store.mapper;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.seoulchonnom.aggregate.trip.store.jpo.TripJpo;
import com.seoulchonnom.aggregate.trip.store.jpo.TripQuizJpo;
import com.seoulchonnom.aggregate.trip.store.jpo.TripQuizOptionJpo;
import com.seoulchonnom.spec.trip.entity.Trip;

@SpringJUnitConfig
@ContextConfiguration(classes = {TripJpoMapperImpl.class, TripQuizJpoMapperImpl.class, TripQuizOptionJpoMapperImpl.class})
class TripJpoMapperTest {
	@Autowired
	private TripJpoMapper tripJpoMapper;

	@Test
	void toDomain_shouldPreserveInheritedAndNestedFields() {
		TripJpo tripJpo = new TripJpo();
		tripJpo.setId("TRIP-1");
		tripJpo.setEntityVersion(7L);
		tripJpo.setRegisteredTime(100L);
		tripJpo.setModifiedTime(200L);
		tripJpo.setDate("2026-03-31");
		tripJpo.setType("ryu");
		tripJpo.setName("Trip Name");
		tripJpo.setLogo("logo.png");

		TripQuizJpo tripQuizJpo = new TripQuizJpo();
		tripQuizJpo.setId("4a0e0a8d-3e40-41c7-9d67-4d26cf6c62be");
		tripQuizJpo.setTrip(tripJpo);
		tripQuizJpo.setTitle("Quiz Title");
		tripQuizJpo.setCorrectOptionId("OPTION-2");
		tripQuizJpo.setAnswerTitle("Answer Title");
		tripQuizJpo.setAnswerText("Answer Text");
		tripQuizJpo.setErrorTitle("Error Title");
		tripQuizJpo.setErrorText("Error Text");

		TripQuizOptionJpo option1 = new TripQuizOptionJpo();
		option1.setId("4a0e0a8d-3e40-41c7-9d67-4d26cf6c62be");
		option1.setText("wrong");
		option1.setSortOrder(2);
		option1.setQuiz(tripQuizJpo);

		TripQuizOptionJpo option2 = new TripQuizOptionJpo();
		option2.setId("OPTION-2");
		option2.setText("right");
		option2.setSortOrder(1);
		option2.setQuiz(tripQuizJpo);

		tripQuizJpo.setOptions(List.of(option1, option2));
		tripQuizJpo.setTrip(tripJpo);
		tripJpo.setQuiz(tripQuizJpo);

		Trip trip = tripJpoMapper.toDomain(tripJpo);

		assertThat(trip.getId()).isEqualTo("TRIP-1");
		assertThat(trip.getEntityVersion()).isEqualTo(7L);
		assertThat(trip.getRegisteredTime()).isEqualTo(100L);
		assertThat(trip.getModifiedTime()).isEqualTo(200L);
		assertThat(trip.getDate()).isEqualTo("2026-03-31");
		assertThat(trip.getQuiz().getCorrectOptionId()).isEqualTo("OPTION-2");
		assertThat(trip.getQuiz().getOptions()).extracting("id").containsExactly("OPTION-2", "OPTION-1");
	}
}
