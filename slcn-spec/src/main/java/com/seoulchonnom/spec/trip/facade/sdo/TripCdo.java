package com.seoulchonnom.spec.trip.facade.sdo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TripCdo {
	private String date;
	private String type;
	private String name;
	private String logo;

	private String firstMap;
	private String secondMap;

	private String nextButtonText;
	private String previousButtonText;

	private String driveUrl;

	private String quizTitle;
	private String quizAnswer;
	private String quizAnswerTitle;
	private String quizAnswerText;
	private String quizErrorTitle;
	private String quizErrorText;

	private List<QuizCdo> quizCdoList;
}
