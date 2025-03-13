package com.seoulchonnom.slcnapp.trip.dto;

import com.seoulchonnom.slcnapp.trip.domain.Trip;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TripRegisterRequest {
	private String date;
	private String type;
	private String info1;
	private String info2;
	private String button1;
	private String button2;
	private String drive;
	private String quizTitle;
	private String quizAnswer;
	private String quizAnswerTitle;
	private String quizAnswerText;
	private String quizErrorTitle;
	private String quizErrorText;
	private List<QuizRegisterRequest> quizRegisterRequestList;

	public Trip of(String logo, String map1) {
		return Trip.builder()
			.date(date)
			.type(type)
			.info1(info1)
			.info2(info2)
			.logo(logo)
			.map1(map1)
			.button1(button1)
			.button2(button2)
			.drive(drive)
			.quizTitle(quizTitle)
			.quizAnswer(quizAnswer)
			.quizAnswerTitle(quizAnswerTitle)
			.quizAnswerText(quizAnswerText)
			.quizErrorTitle(quizErrorTitle)
			.quizErrorText(quizErrorText)
			.build();
	}
}
