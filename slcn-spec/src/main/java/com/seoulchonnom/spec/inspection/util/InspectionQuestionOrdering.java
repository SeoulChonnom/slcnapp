package com.seoulchonnom.spec.inspection.util;

import java.util.Comparator;
import java.util.Map;

import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestionCategory;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;

/**
 * 질문/답변 정렬을 한 곳에 모은다(계획 §2). 저장 순서에 기대지 않고 읽을 때 정렬해서,
 * 분류 지정이 나중에 바뀌어도(백필 포함) 배열 순서를 다시 저장할 필요가 없게 한다.
 *
 * 도메인 엔티티만 다루고 DTO는 모른다(docs/learning/domain-entity-must-not-depend-on-api-dto.md).
 */
public final class InspectionQuestionOrdering {
	private InspectionQuestionOrdering() {
		throw new UnsupportedOperationException("Utility class");
	}

	/**
	 * 질문 목록 정렬: 분류.sortOrder(null이면 맨 뒤) -> 질문.sortOrder -> questionId.
	 * categoryId가 null이거나 categoriesById에 없는 질문(과도기 미분류)은 맨 뒤로 밀린다.
	 *
	 * @param categoriesById 정렬에 쓸 분류를 id로 찾는 맵. 정렬 대상 질문들의 categoryId를
	 *                        모두 포함해야 한다 - 빠지면 그 질문은 미분류로 취급된다
	 */
	public static Comparator<InspectionQuestion> questionComparator(
		Map<String, InspectionQuestionCategory> categoriesById) {
		return Comparator
			.comparing((InspectionQuestion question) -> categorySortOrderOf(question, categoriesById),
				Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparingInt(InspectionQuestion::getSortOrder)
			.thenComparing(InspectionQuestion::getId);
	}

	private static Integer categorySortOrderOf(InspectionQuestion question,
		Map<String, InspectionQuestionCategory> categoriesById) {
		if (question.getCategoryId() == null) {
			return null;
		}
		InspectionQuestionCategory category = categoriesById.get(question.getCategoryId());
		return category == null ? null : category.getSortOrder();
	}

	/**
	 * 답변 스냅샷 정렬: 분류.sortOrder(null이면 맨 뒤) -> 답변.sortOrder -> questionId.
	 *
	 * 질문용과 규칙은 같지만 마스터를 다시 찾지 않는다 - PropertyAnswer가 생성 시점의 분류 값을
	 * 그대로 들고 있어(계획 §1), 분류 맵을 받을 필요 없이 답변 스냅샷만으로 정렬할 수 있다.
	 */
	public static Comparator<PropertyAnswer> answerComparator() {
		return Comparator
			.comparing(PropertyAnswer::getCategorySortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparingInt(PropertyAnswer::getSortOrder)
			.thenComparing(PropertyAnswer::getQuestionId);
	}
}
