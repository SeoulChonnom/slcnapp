package com.seoulchonnom.aggregate.house.collect;

/**
 * 단지 좌표 조회 결과. 좌표는 WGS84다.
 *
 * @param roadAddress FOUND일 때 행안부 검색으로 확정한 도로명주소
 */
public record GeocodeResult(Outcome outcome, Double latitude, Double longitude, String roadAddress) {

	public enum Outcome {
		FOUND,
		/** 모든 검색어에서 후보나 좌표가 없다. 다시 해도 같으므로 즉시 FAILED다. */
		NOT_FOUND,
		/** 행안부 호출 실패(502 계열). 시도 횟수를 올릴지는 Flow가 정한다(연속 실패면 올리지 않는다). */
		UPSTREAM_FAILED,
		/** 행안부 키가 없거나 거절됐다. 좌표 처리를 접는다. */
		NOT_CONFIGURED,
		/** 호출 예산이 없다. 단지 상태는 그대로 둔다. */
		BUDGET_EXHAUSTED,
		/** 호출 간격 대기 중 인터럽트. 단지 상태는 그대로 두고 멈춘다. */
		INTERRUPTED
	}

	public static GeocodeResult found(double latitude, double longitude, String roadAddress) {
		return new GeocodeResult(Outcome.FOUND, latitude, longitude, roadAddress);
	}

	public static GeocodeResult of(Outcome outcome) {
		return new GeocodeResult(outcome, null, null, null);
	}
}
