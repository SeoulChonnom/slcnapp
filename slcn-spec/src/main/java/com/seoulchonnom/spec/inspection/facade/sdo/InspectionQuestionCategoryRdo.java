package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * enabledQuestionCount는 이 분류에 속한 활성 질문 수다. 목록 조회가 항상 집계를 끌고 간다 —
 * 관리 화면이 비활성화 가능 여부를 이 값으로 미리 판단한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InspectionQuestionCategoryRdo {
	private String categoryId;
	private String name;
	private int sortOrder;
	private boolean enabled;
	private int enabledQuestionCount;
}
