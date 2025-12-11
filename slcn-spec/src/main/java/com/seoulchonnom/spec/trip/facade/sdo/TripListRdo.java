package com.seoulchonnom.spec.trip.facade.sdo;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TripListRdo {
	private String date;
	private String info1;
	private String info2;
	private String logo;
	private String quizTitle;
	private String quizAnswer;
	private String quizAnswerTitle;
	private String quizAnswerText;
	private String quizErrorTitle;
	private String quizErrorText;
	private List<QuizRdo> quizResponses;
}
