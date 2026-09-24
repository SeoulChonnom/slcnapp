package com.seoulchonnom.spec.inspection.entity.vo;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class PropertyAnswerTest {
	private static PropertyAnswer answerOf(QuestionAnswerType answerType) {
		PropertyAnswer answer = new PropertyAnswer();
		answer.setAnswerType(answerType);
		return answer;
	}

	@Test
	void refreshAnswered_shouldTreatBlankTextAsUnanswered() {
		PropertyAnswer answer = answerOf(QuestionAnswerType.LONG_TEXT);
		answer.setTextValue("   ");
		answer.refreshAnswered();

		assertThat(answer.isAnswered()).isFalse();
	}

	@Test
	void refreshAnswered_shouldTreatFalseBooleanAsAnswered() {
		PropertyAnswer answer = answerOf(QuestionAnswerType.BOOLEAN);
		answer.setBooleanValue(false);
		answer.refreshAnswered();

		assertThat(answer.isAnswered()).isTrue();
	}

	@Test
	void refreshAnswered_shouldTreatZeroNumberAsAnswered() {
		PropertyAnswer answer = answerOf(QuestionAnswerType.NUMBER);
		answer.setNumberValue(BigDecimal.ZERO);
		answer.refreshAnswered();

		assertThat(answer.isAnswered()).isTrue();
	}

	@Test
	void refreshAnswered_shouldRequireExactlyOneCodeForSingleSelect() {
		PropertyAnswer answer = answerOf(QuestionAnswerType.SINGLE_SELECT);
		answer.setSelectedCodes(List.of("SOUTH", "EAST"));
		answer.refreshAnswered();

		assertThat(answer.isAnswered()).isFalse();

		answer.setSelectedCodes(List.of("SOUTH"));
		answer.refreshAnswered();

		assertThat(answer.isAnswered()).isTrue();
	}

	@Test
	void refreshAnswered_shouldAcceptMultipleCodesForMultiSelect() {
		PropertyAnswer answer = answerOf(QuestionAnswerType.MULTI_SELECT);
		answer.setSelectedCodes(List.of("SOUTH", "EAST"));
		answer.refreshAnswered();

		assertThat(answer.isAnswered()).isTrue();
	}

	@Test
	void refreshAnswered_shouldClearWhenValueIsRemoved() {
		PropertyAnswer answer = answerOf(QuestionAnswerType.RATING);
		answer.setRatingValue(4);
		answer.refreshAnswered();
		assertThat(answer.isAnswered()).isTrue();

		answer.setRatingValue(null);
		answer.refreshAnswered();
		assertThat(answer.isAnswered()).isFalse();
	}
}
