package com.seoulchonnom.aggregate.trip.store.projection;

import java.util.List;

public class TripListPdo {
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
	private List<QuizPdo> quizResponses;
}
