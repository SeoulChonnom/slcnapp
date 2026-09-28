package com.seoulchonnom.spec.inspection.util;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestionCategory;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;

class InspectionQuestionOrderingTest {
	private static InspectionQuestion question(String id, String categoryId, int sortOrder) {
		InspectionQuestion question = new InspectionQuestion(id, QuestionAnswerType.TEXT, false, sortOrder);
		question.setCategoryId(categoryId);
		return question;
	}

	private static PropertyAnswer answer(String questionId, Integer categorySortOrder, int sortOrder) {
		PropertyAnswer answer = new PropertyAnswer();
		answer.setQuestionId(questionId);
		answer.setCategorySortOrder(categorySortOrder);
		answer.setSortOrder(sortOrder);
		return answer;
	}

	@Test
	void questionComparator_shouldOrderByCategorySortOrderFirst() {
		InspectionQuestionCategory categoryA = new InspectionQuestionCategory("CATEGORY-A", "채광", 2);
		InspectionQuestionCategory categoryB = new InspectionQuestionCategory("CATEGORY-B", "구조", 1);
		Map<String, InspectionQuestionCategory> categoriesById = Map.of(
			"CATEGORY-A", categoryA, "CATEGORY-B", categoryB);

		// 분류 안에서의 sortOrder만 보면 q1이 먼저지만, 분류 자체의 순서(B=1 < A=2)가 우선이다.
		InspectionQuestion q1 = question("Q-1", "CATEGORY-A", 1);
		InspectionQuestion q2 = question("Q-2", "CATEGORY-B", 5);

		List<InspectionQuestion> sorted = List.of(q1, q2).stream()
			.sorted(InspectionQuestionOrdering.questionComparator(categoriesById))
			.toList();

		assertThat(sorted).containsExactly(q2, q1);
	}

	@Test
	void questionComparator_shouldFallBackToQuestionSortOrderWithinSameCategory() {
		InspectionQuestionCategory category = new InspectionQuestionCategory("CATEGORY-A", "채광", 1);
		Map<String, InspectionQuestionCategory> categoriesById = Map.of("CATEGORY-A", category);

		InspectionQuestion q1 = question("Q-1", "CATEGORY-A", 3);
		InspectionQuestion q2 = question("Q-2", "CATEGORY-A", 1);

		List<InspectionQuestion> sorted = List.of(q1, q2).stream()
			.sorted(InspectionQuestionOrdering.questionComparator(categoriesById))
			.toList();

		assertThat(sorted).containsExactly(q2, q1);
	}

	@Test
	void questionComparator_shouldFallBackToIdOnFullTie() {
		InspectionQuestionCategory category = new InspectionQuestionCategory("CATEGORY-A", "채광", 1);
		Map<String, InspectionQuestionCategory> categoriesById = Map.of("CATEGORY-A", category);

		InspectionQuestion q2 = question("Q-2", "CATEGORY-A", 1);
		InspectionQuestion q1 = question("Q-1", "CATEGORY-A", 1);

		List<InspectionQuestion> sorted = List.of(q2, q1).stream()
			.sorted(InspectionQuestionOrdering.questionComparator(categoriesById))
			.toList();

		assertThat(sorted).containsExactly(q1, q2);
	}

	/**
	 * categoryId가 null인 과도기 질문(계획 §0-1)은 맨 뒤로 밀린다.
	 */
	@Test
	void questionComparator_shouldPushNullCategoryToTheEnd() {
		InspectionQuestionCategory category = new InspectionQuestionCategory("CATEGORY-A", "채광", 100);
		Map<String, InspectionQuestionCategory> categoriesById = Map.of("CATEGORY-A", category);

		InspectionQuestion uncategorized = question("Q-1", null, 1);
		InspectionQuestion categorized = question("Q-2", "CATEGORY-A", 1);

		List<InspectionQuestion> sorted = List.of(uncategorized, categorized).stream()
			.sorted(InspectionQuestionOrdering.questionComparator(categoriesById))
			.toList();

		assertThat(sorted).containsExactly(categorized, uncategorized);
	}

	/**
	 * categoryId는 있지만 맵에 없는 분류(예: 조회 시 누락)도 미분류와 같이 맨 뒤로 밀린다.
	 */
	@Test
	void questionComparator_shouldPushUnknownCategoryToTheEnd() {
		InspectionQuestionCategory category = new InspectionQuestionCategory("CATEGORY-A", "채광", 1);
		Map<String, InspectionQuestionCategory> categoriesById = Map.of("CATEGORY-A", category);

		InspectionQuestion unknown = question("Q-1", "CATEGORY-MISSING", 1);
		InspectionQuestion known = question("Q-2", "CATEGORY-A", 1);

		List<InspectionQuestion> sorted = List.of(unknown, known).stream()
			.sorted(InspectionQuestionOrdering.questionComparator(categoriesById))
			.toList();

		assertThat(sorted).containsExactly(known, unknown);
	}

	@Test
	void answerComparator_shouldOrderByCategorySortOrderFirst() {
		// 분류 안에서의 sortOrder만 보면 a1이 먼저지만, 분류 자체의 순서(categorySortOrder 1 < 2)가 우선이다.
		PropertyAnswer a1 = answer("Q-1", 2, 1);
		PropertyAnswer a2 = answer("Q-2", 1, 5);

		List<PropertyAnswer> sorted = List.of(a1, a2).stream()
			.sorted(InspectionQuestionOrdering.answerComparator())
			.toList();

		assertThat(sorted).containsExactly(a2, a1);
	}

	@Test
	void answerComparator_shouldFallBackToSortOrderWithinSameCategory() {
		PropertyAnswer a1 = answer("Q-1", 1, 3);
		PropertyAnswer a2 = answer("Q-2", 1, 1);

		List<PropertyAnswer> sorted = List.of(a1, a2).stream()
			.sorted(InspectionQuestionOrdering.answerComparator())
			.toList();

		assertThat(sorted).containsExactly(a2, a1);
	}

	@Test
	void answerComparator_shouldFallBackToQuestionIdOnFullTie() {
		PropertyAnswer a2 = answer("Q-2", 1, 1);
		PropertyAnswer a1 = answer("Q-1", 1, 1);

		List<PropertyAnswer> sorted = List.of(a2, a1).stream()
			.sorted(InspectionQuestionOrdering.answerComparator())
			.toList();

		assertThat(sorted).containsExactly(a1, a2);
	}

	/**
	 * categorySortOrder가 null인 답변(과도기/백필 전 스냅샷)은 맨 뒤로 밀린다.
	 */
	@Test
	void answerComparator_shouldPushNullCategorySortOrderToTheEnd() {
		PropertyAnswer uncategorized = answer("Q-1", null, 1);
		PropertyAnswer categorized = answer("Q-2", 100, 1);

		List<PropertyAnswer> sorted = List.of(uncategorized, categorized).stream()
			.sorted(InspectionQuestionOrdering.answerComparator())
			.toList();

		assertThat(sorted).containsExactly(categorized, uncategorized);
	}
}
