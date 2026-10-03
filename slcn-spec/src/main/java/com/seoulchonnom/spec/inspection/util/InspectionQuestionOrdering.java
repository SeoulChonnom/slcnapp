package com.seoulchonnom.spec.inspection.util;

import java.util.Comparator;
import java.util.Map;

import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestionCategory;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;

/**
 * 질문/답변 정렬을 한 곳에 모은다(계획 §2). 저장 순서에 기대지 않고 읽을 때 정렬해서,
 * 스냅샷 배열의 순서가 어떻게 저장돼 있든 응답 순서가 같게 한다.
 *
 * 도메인 엔티티만 다루고 DTO는 모른다(docs/learning/domain-entity-must-not-depend-on-api-dto.md).
 */
public final class InspectionQuestionOrdering {
	private InspectionQuestionOrdering() {
		throw new UnsupportedOperationException("Utility class");
	}

	/**
	 * 질문 목록 정렬: 분류.sortOrder -> 질문.sortOrder -> questionId.
	 *
	 * @param categoriesById 정렬에 쓸 분류를 id로 찾는 맵. 정렬 대상 질문들의 categoryId를
	 *                        모두 포함해야 한다
	 */
	public static Comparator<InspectionQuestion> questionComparator(
		Map<String, InspectionQuestionCategory> categoriesById) {
		return Comparator
			.comparingInt((InspectionQuestion question) -> categorySortOrderOf(question, categoriesById))
			.thenComparingInt(InspectionQuestion::getSortOrder)
			.thenComparing(InspectionQuestion::getId);
	}

	/**
	 * 분류가 맵에 없으면 호출자가 분류를 덜 읽은 것이다. 조용히 순서를 정하지 않고 드러낸다.
	 */
	private static int categorySortOrderOf(InspectionQuestion question,
		Map<String, InspectionQuestionCategory> categoriesById) {
		InspectionQuestionCategory category = categoriesById.get(question.getCategoryId());
		if (category == null) {
			throw new IllegalStateException("정렬할 질문의 분류가 없습니다. questionId=" + question.getId()
				+ ", categoryId=" + question.getCategoryId());
		}
		return category.getSortOrder();
	}

	/**
	 * 답변 스냅샷 정렬: 분류.sortOrder -> 답변.sortOrder -> questionId.
	 *
	 * 질문용과 규칙은 같지만 마스터를 다시 찾지 않는다 - PropertyAnswer가 생성 시점의 분류 값을
	 * 그대로 들고 있어(계획 §1), 분류 맵을 받을 필요 없이 답변 스냅샷만으로 정렬할 수 있다.
	 */
	public static Comparator<PropertyAnswer> answerComparator() {
		return Comparator
			.comparingInt(PropertyAnswer::getCategorySortOrder)
			.thenComparingInt(PropertyAnswer::getSortOrder)
			.thenComparing(PropertyAnswer::getQuestionId);
	}
}
