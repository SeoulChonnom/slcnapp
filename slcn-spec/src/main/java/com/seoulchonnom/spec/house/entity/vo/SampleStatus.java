package com.seoulchonnom.spec.house.entity.vo;

/**
 * 비교 구간의 표본 충분도. 두 구간 중 건수가 적은 쪽을 기준으로 한다.
 */
public enum SampleStatus {
	OK, LOW, INSUFFICIENT;

	public static SampleStatus of(int cntFrom, int cntTo) {
		int min = Math.min(cntFrom, cntTo);
		if (min < 2) {
			return INSUFFICIENT;
		}
		if (min < 3) {
			return LOW;
		}
		return OK;
	}
}
