package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 수집 정책 변경. 버전을 올리지 않는다 — 진행 중인 매물에는 스냅샷이 이미 고정되어 있다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionQuestionPolicyUdo {
	private boolean required;
	private int sortOrder;
	/**
	 * 조회 시 받은 entityVersion을 그대로 되돌려 보내야 한다(C-1). null이면 400으로 거절한다.
	 */
	private Long entityVersion;
}
