package com.seoulchonnom.slcnapp.trip.dto;

import com.seoulchonnom.slcnapp.trip.domain.Quiz;
import com.seoulchonnom.slcnapp.trip.domain.Trip;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuizRegisterRequest {
	private String quizIndex;
	private String answer;

	public Quiz of(Trip trip) {
		return Quiz.builder().quizIndex(quizIndex).trip(trip).answer(answer).build();
	}
}
