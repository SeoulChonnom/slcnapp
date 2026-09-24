package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * "지금 물어볼 질문인가"만 답한다. 과거 답변은 enabled와 무관하게 그대로 조회된다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionQuestionStatusUdo {
	private boolean enabled;
}
