package com.seoulchonnom.spec.trip.entity;

import java.util.List;

import com.seoulchonnom.spec.common.entity.DomainEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
@Setter
public class Trip extends DomainEntity {
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

	private List<Quiz> quizList;
}
