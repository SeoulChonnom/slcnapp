package com.seoulchonnom.aggregate.trip.store.mapper;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.seoulchonnom.aggregate.trip.store.jpo.QuizJpo;
import com.seoulchonnom.aggregate.trip.store.jpo.TripJpo;
import com.seoulchonnom.spec.trip.entity.Trip;

@SpringJUnitConfig
@ContextConfiguration(classes = {TripJpoMapperImpl.class, QuizJpoMapperImpl.class})
class TripJpoMapperTest {

	@Autowired
	private TripJpoMapper tripJpoMapper;

	@Test
	void toDomain_shouldPreserveInheritedAndNestedFields() {
		QuizJpo quizJpo = new QuizJpo();
		quizJpo.setId("QUIZ-1");
		quizJpo.setTripId("TRIP-1");
		quizJpo.setQuizIndex("1");
		quizJpo.setAnswer("A");

		TripJpo tripJpo = new TripJpo();
		tripJpo.setId("TRIP-1");
		tripJpo.setEntityVersion(7L);
		tripJpo.setRegisteredTime(100L);
		tripJpo.setModifiedTime(200L);
		tripJpo.setDate("2026-03-31");
		tripJpo.setName("Trip Name");
		tripJpo.setQuizList(List.of(quizJpo));

		Trip trip = tripJpoMapper.toDomain(tripJpo);

		assertThat(trip.getId()).isEqualTo("TRIP-1");
		assertThat(trip.getEntityVersion()).isEqualTo(7L);
		assertThat(trip.getRegisteredTime()).isEqualTo(100L);
		assertThat(trip.getModifiedTime()).isEqualTo(200L);
		assertThat(trip.getDate()).isEqualTo("2026-03-31");
		assertThat(trip.getName()).isEqualTo("Trip Name");
		assertThat(trip.getQuizList()).singleElement().satisfies(quiz -> {
			assertThat(quiz.getId()).isEqualTo("QUIZ-1");
			assertThat(quiz.getTripId()).isEqualTo("TRIP-1");
			assertThat(quiz.getAnswer()).isEqualTo("A");
		});
	}
}
