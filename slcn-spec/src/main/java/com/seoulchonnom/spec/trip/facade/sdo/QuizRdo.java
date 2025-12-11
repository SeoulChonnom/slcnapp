package com.seoulchonnom.spec.trip.facade.sdo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuizRdo {
	private String quizIndex;
	private String answer;
}
