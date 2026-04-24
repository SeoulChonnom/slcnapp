package com.seoulchonnom.spec.trip.entity.vo;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.common.entity.vo.JsonSerializable;
import com.seoulchonnom.spec.trip.facade.sdo.OptionCdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizCdo;

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
public class Quiz implements JsonSerializable {
	private String id;
	private String title;
	private String correctOptionId;
	private String answerTitle;
	private String answerText;
	private String errorTitle;
	private String errorText;
	private List<Option> options;

	public Quiz(QuizCdo quizCdo) {
		this.title = quizCdo.getTitle();
		this.answerTitle = quizCdo.getAnswerTitle();
		this.answerText = quizCdo.getAnswerText();
		this.errorTitle = quizCdo.getErrorTitle();
		this.errorText = quizCdo.getErrorText();
		this.options = new ArrayList<>();

		int cnt = 1;

		for (OptionCdo optionCdo : quizCdo.getOptions()) {
			String optionId = "OPT" + cnt;
			Option option = new Option(optionCdo, optionId);
			this.options.add(option);
			if (optionCdo.isCorrect()) {
				this.correctOptionId = option.getId();
			}
			cnt = cnt + 1;
		}
	}
}
