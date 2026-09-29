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

	/**
	 * JSON 컬럼 더티 체크 방지의 핵심(json-column-value-objects-need-equals 학습) - 새로 추가한
	 * 분류 스냅샷 필드도 equals 비교 대상에서 빠지면 안 된다.
	 */
	@Test
	void equals_shouldIncludeCategorySnapshotFields() {
		PropertyAnswer withCategory = answerWithCategory("CATEGORY-1", "채광·환기", 1);
		PropertyAnswer sameCategory = answerWithCategory("CATEGORY-1", "채광·환기", 1);

		assertThat(withCategory).isEqualTo(sameCategory);
		assertThat(withCategory).isNotEqualTo(answerWithCategory("CATEGORY-2", "채광·환기", 1));
		assertThat(withCategory).isNotEqualTo(answerWithCategory("CATEGORY-1", "구조", 1));
		assertThat(withCategory).isNotEqualTo(answerWithCategory("CATEGORY-1", "채광·환기", 2));
	}

	private PropertyAnswer answerWithCategory(String categoryId, String categoryName, int categorySortOrder) {
		PropertyAnswer answer = answerOf(QuestionAnswerType.TEXT);
		answer.setQuestionId("q1");
		answer.setCategoryId(categoryId);
		answer.setCategoryName(categoryName);
		answer.setCategorySortOrder(categorySortOrder);
		return answer;
	}
}
