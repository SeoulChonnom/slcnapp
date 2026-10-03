package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * enabled=false인데 활성 질문이 있으면 409다(계획 §0-3).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionQuestionCategoryStatusUdo {
	private boolean enabled;
}
