package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 질문을 다른 분류로 옮긴다. 항상 대상 분류의 맨 뒤(max(sortOrder)+1)에 배치되고,
 * 버전은 올리지 않는다(계획 §1).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionQuestionCategoryMoveUdo {
	private String categoryId;
}
