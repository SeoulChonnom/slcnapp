package com.seoulchonnom.slcnapp.trip.dto;

import java.util.List;
import java.util.stream.Collectors;

import com.seoulchonnom.slcnapp.trip.domain.Trip;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TripListResponse {
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
	private List<QuizResponse> quizResponses;

	public static TripListResponse from(Trip trip) {
		return TripListResponse.builder()
			.date(trip.getDate())
			.info1(trip.getInfo1())
			.info2(trip.getInfo2())
			.logo(trip.getLogo())
			.quizTitle(trip.getQuizTitle())
			.quizAnswer(trip.getQuizAnswer())
			.quizAnswerTitle(trip.getQuizAnswerTitle())
			.quizAnswerText(trip.getQuizAnswerText())
			.quizErrorTitle(trip.getQuizErrorTitle())
			.quizErrorText(trip.getQuizErrorText())
			.quizResponses(trip.getQuizList().stream().map(QuizResponse::from).collect(Collectors.toList()))
			.build();
	}
}
