package com.seoulchonnom.spec.trip.entity;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.trip.facade.sdo.TripQuizCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripQuizOptionCdo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class TripQuiz {
	private String tripId;
	private String title;
	private String correctOptionId;
	private String answerTitle;
	private String answerText;
	private String errorTitle;
	private String errorText;
	private List<TripQuizOption> options;

	public TripQuiz(TripQuizCdo tripQuizCdo, String tripId) {
		this.tripId = tripId;
		this.title = tripQuizCdo.getTitle();
		this.answerTitle = tripQuizCdo.getAnswerTitle();
		this.answerText = tripQuizCdo.getAnswerText();
		this.errorTitle = tripQuizCdo.getErrorTitle();
		this.errorText = tripQuizCdo.getErrorText();
		this.options = new ArrayList<>();

		for (TripQuizOptionCdo optionCdo : tripQuizCdo.getOptions()) {
			TripQuizOption option = new TripQuizOption(optionCdo, tripId);
			this.options.add(option);
			if (optionCdo.isCorrect()) {
				this.correctOptionId = option.getId();
			}
		}
	}
}
