package com.seoulchonnom.spec.inspection.entity;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;

class ViewedPropertyTest {
	private static PropertyAnswer answer(String questionId, boolean required, boolean answered) {
		PropertyAnswer propertyAnswer = new PropertyAnswer();
		propertyAnswer.setQuestionId(questionId);
		propertyAnswer.setAnswerType(QuestionAnswerType.TEXT);
		propertyAnswer.setRequired(required);
		propertyAnswer.setAnswered(answered);
		return propertyAnswer;
	}

	private static ViewedProperty property() {
		return new ViewedProperty("INSPECTION_VISIT-0001", "트리마제", "101동 1203호", 1);
	}

	@Test
	void refreshAnswerCounts_shouldCountOnlyRequiredAnswers() {
		ViewedProperty viewedProperty = property();
		viewedProperty.setAnswers(List.of(
			answer("q1", true, true),
			answer("q2", true, false),
			answer("q3", false, false)));

		viewedProperty.refreshAnswerCounts();

		assertThat(viewedProperty.getRequiredAnswerCount()).isEqualTo(2);
		assertThat(viewedProperty.getUnansweredRequiredCount()).isEqualTo(1);
	}

	@Test
	void refreshAnswerCounts_shouldFollowAnswerValueChange() {
		ViewedProperty viewedProperty = property();
		PropertyAnswer required = answer("q1", true, false);
		viewedProperty.setAnswers(List.of(required));
		viewedProperty.refreshAnswerCounts();
		assertThat(viewedProperty.getUnansweredRequiredCount()).isEqualTo(1);

		required.setTextValue("오후에도 밝았다");
		required.refreshAnswered();
		viewedProperty.refreshAnswerCounts();

		assertThat(viewedProperty.getUnansweredRequiredCount()).isZero();
	}

	@Test
	void refreshAnswerCounts_shouldBeZeroWhenThereIsNoQuestion() {
		ViewedProperty viewedProperty = property();

		viewedProperty.refreshAnswerCounts();

		assertThat(viewedProperty.getRequiredAnswerCount()).isZero();
		assertThat(viewedProperty.getUnansweredRequiredCount()).isZero();
	}

	@Test
	void findAnswer_shouldLocateByQuestionId() {
		ViewedProperty viewedProperty = property();
		viewedProperty.setAnswers(List.of(answer("q1", true, false), answer("q2", false, false)));

		assertThat(viewedProperty.findAnswer("q2")).isPresent();
		assertThat(viewedProperty.findAnswer("q9")).isEmpty();
		assertThat(viewedProperty.findAnswer(null)).isEmpty();
	}

	@Test
	void newProperty_shouldStartAsDraftWithoutAnswers() {
		ViewedProperty viewedProperty = property();

		assertThat(viewedProperty.getStatus().name()).isEqualTo("DRAFT");
		assertThat(viewedProperty.getAnswers()).isEmpty();
	}
}
