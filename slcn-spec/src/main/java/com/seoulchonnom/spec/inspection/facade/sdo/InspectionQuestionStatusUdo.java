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
	/**
	 * 조회 시 받은 entityVersion을 그대로 되돌려 보내야 한다(C-1). null이면 400으로 거절한다.
	 */
	private Long entityVersion;
}
