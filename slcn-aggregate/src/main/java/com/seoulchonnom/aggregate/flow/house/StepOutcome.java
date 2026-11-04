package com.seoulchonnom.aggregate.flow.house;

import com.seoulchonnom.spec.house.entity.vo.BatchRunStatus;

/**
 * 단계 본문이 돌려주는 최종 상태. error에는 우리가 만든 짧은 사유만 넣는다(외부 응답 본문·URL 금지).
 */
public record StepOutcome(BatchRunStatus status, String error) {
	public static StepOutcome of(BatchRunStatus status) {
		return new StepOutcome(status, null);
	}

	public static StepOutcome of(BatchRunStatus status, String error) {
		return new StepOutcome(status, error);
	}
}
