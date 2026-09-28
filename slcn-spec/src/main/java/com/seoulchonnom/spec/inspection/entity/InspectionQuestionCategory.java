package com.seoulchonnom.spec.inspection.entity;

import com.seoulchonnom.spec.common.entity.DomainEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 질문 대분류. 물리 삭제 경로는 두지 않는다 — enabled=false만 있다.
 * 정렬은 분류.sortOrder -> 질문.sortOrder -> questionId 순이다(계획 §2, InspectionQuestionOrdering은 이후 단계에서 둔다).
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class InspectionQuestionCategory extends DomainEntity {
	private String name;
	private int sortOrder;
	private boolean enabled;

	public InspectionQuestionCategory(String id, String name, int sortOrder) {
		super(id);
		this.name = name;
		this.sortOrder = sortOrder;
		this.enabled = true;
	}

	public void rename(String name) {
		this.name = name;
		this.modifiedTime = System.currentTimeMillis();
	}

	public void changeSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
		this.modifiedTime = System.currentTimeMillis();
	}

	/**
	 * 비활성화 가능 조건(활성 질문이 0개)은 이 엔티티가 알지 못한다. 분류는 자기 소속 질문을
	 * 조회할 수 없으므로 그 검증은 Logic이 InspectionQuestionStore를 통해 확인한다.
	 */
	public void changeEnabled(boolean enabled) {
		this.enabled = enabled;
		this.modifiedTime = System.currentTimeMillis();
	}
}
