package com.seoulchonnom.spec.trip.mapper;

import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import com.seoulchonnom.spec.trip.entity.Quiz;
import com.seoulchonnom.spec.trip.entity.Trip;
import com.seoulchonnom.spec.trip.facade.sdo.QuizRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripInfoRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripListRdo;

@Component
public class TripMapper {
	public TripListRdo toTripListRdo(Trip trip) {
		TripListRdo tripListRdo = new TripListRdo();
		BeanUtils.copyProperties(trip, tripListRdo, "quizList");
		tripListRdo.setQuizList(toQuizRdoList(trip.getQuizList()));
		return tripListRdo;
	}

	public TripInfoRdo toTripInfoRdo(Trip trip) {
		TripInfoRdo tripInfoRdo = new TripInfoRdo();
		BeanUtils.copyProperties(trip, tripInfoRdo);
		tripInfoRdo.setDrive(trip.getDriveUrl());
		return tripInfoRdo;
	}

	public QuizRdo toQuizRdo(Quiz quiz) {
		QuizRdo quizRdo = new QuizRdo();
		BeanUtils.copyProperties(quiz, quizRdo);
		return quizRdo;
	}

	private List<QuizRdo> toQuizRdoList(List<Quiz> quizList) {
		if (quizList == null) {
			return List.of();
		}

		return quizList.stream()
			.map(this::toQuizRdo)
			.toList();
	}
}
