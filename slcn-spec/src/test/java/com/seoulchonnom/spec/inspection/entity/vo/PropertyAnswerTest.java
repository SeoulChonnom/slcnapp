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

	@Test
	void assignCategory_shouldFillWhenCategoryIdIsNull() {
		PropertyAnswer answer = answerOf(QuestionAnswerType.TEXT);

		boolean changed = answer.assignCategory("CATEGORY-1", "채광·환기", 1);

		assertThat(changed).isTrue();
		assertThat(answer.getCategoryId()).isEqualTo("CATEGORY-1");
		assertThat(answer.getCategoryName()).isEqualTo("채광·환기");
		assertThat(answer.getCategorySortOrder()).isEqualTo(1);
	}

	/**
	 * 백필은 재실행돼도 안전해야 한다(계획 §5) - 이미 분류가 있으면 마스터 기준 값으로 덮어쓰지 않는다.
	 */
	@Test
	void assignCategory_shouldNotOverwriteExistingCategory() {
		PropertyAnswer answer = answerOf(QuestionAnswerType.TEXT);
		answer.assignCategory("CATEGORY-1", "채광·환기", 1);

		boolean changed = answer.assignCategory("CATEGORY-2", "구조", 2);

		assertThat(changed).isFalse();
		assertThat(answer.getCategoryId()).isEqualTo("CATEGORY-1");
		assertThat(answer.getCategoryName()).isEqualTo("채광·환기");
		assertThat(answer.getCategorySortOrder()).isEqualTo(1);
	}

	/**
	 * JSON 컬럼 더티 체크 방지의 핵심(json-column-value-objects-need-equals 학습) - 새로 추가한
	 * 분류 스냅샷 필드도 equals 비교 대상에서 빠지면 안 된다.
	 */
	@Test
	void equals_shouldIncludeCategorySnapshotFields() {
		PropertyAnswer withoutCategory = answerOf(QuestionAnswerType.TEXT);
		withoutCategory.setQuestionId("q1");
		PropertyAnswer withCategory = answerOf(QuestionAnswerType.TEXT);
		withCategory.setQuestionId("q1");
		withCategory.assignCategory("CATEGORY-1", "채광·환기", 1);

		assertThat(withoutCategory).isNotEqualTo(withCategory);

		PropertyAnswer sameCategory = answerOf(QuestionAnswerType.TEXT);
		sameCategory.setQuestionId("q1");
		sameCategory.assignCategory("CATEGORY-1", "채광·환기", 1);

		assertThat(withCategory).isEqualTo(sameCategory);
	}
}
