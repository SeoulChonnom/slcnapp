package com.seoulchonnom.spec.inspection.util;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestionCategory;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;

class InspectionQuestionOrderingTest {
	private static InspectionQuestion question(String id, String categoryId, int sortOrder) {
		InspectionQuestion question = new InspectionQuestion(id, QuestionAnswerType.TEXT, false, sortOrder);
		question.setCategoryId(categoryId);
		return question;
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
}
