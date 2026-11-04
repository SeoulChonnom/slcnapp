package com.seoulchonnom.aggregate.external.datagokr;

/**
 * 공공데이터포털 응답의 결과 분류. 처리 방침이 같은 것끼리 묶었다.
 */
public enum DataGoKrResultCode {
	/** 정상 */
	OK,
	/** 조회 결과 없음. 오류가 아니라 빈 결과다. */
	NO_DATA,
	/** 인증 키 거절·만료·미등록. 재시도해도 같다. */
	KEY_REJECTED,
	/** 일일 호출 한도 초과. 남은 일은 다음 날 한다. */
	QUOTA_EXCEEDED,
	/** 타임아웃·게이트웨이 오류·해석 불가 등. 재시도 대상이다. */
	UPSTREAM;

	/**
	 * 오류 메시지(errMsg/resultMsg) 또는 XML 오류 본문에서 분류한다. 모르는 값은 UPSTREAM이다.
	 */
	public static DataGoKrResultCode classify(String message) {
		if (message == null) {
			return UPSTREAM;
		}
		if (message.contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR") || message.contains("SERVICE_ACCESS_DENIED_ERROR")
			|| message.contains("UNREGISTERED_IP_ERROR") || message.contains("DEADLINE_HAS_EXPIRED_ERROR")) {
			return KEY_REJECTED;
		}
		if (message.contains("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR")) {
			return QUOTA_EXCEEDED;
		}
		if (message.contains("NODATA_ERROR")) {
			return NO_DATA;
		}
		return UPSTREAM;
	}

	/**
	 * 게이트웨이 단계 오류의 returnReasonCode로 분류한다. errMsg가 비었을 때의 보조 수단이다.
	 */
	static DataGoKrResultCode classifyReasonCode(String reasonCode) {
		if (reasonCode == null) {
			return UPSTREAM;
		}
		return switch (reasonCode.trim()) {
			case "20", "30", "31", "32" -> KEY_REJECTED;
			case "22" -> QUOTA_EXCEEDED;
			default -> UPSTREAM;
		};
	}
}
