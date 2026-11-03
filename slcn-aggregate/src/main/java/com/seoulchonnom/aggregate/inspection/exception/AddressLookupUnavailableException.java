package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

/**
 * 행안부 주소 조회를 쓸 수 없을 때의 예외. 원인에 따라 상태가 다르다.
 * - 503(ADDRESS_LOOKUP_UNAVAILABLE): 키 미설정, 승인되지 않았거나 만료된 키처럼 관리자가 고쳐야 하는 경우
 * - 502(ADDRESS_LOOKUP_FAILED): 타임아웃, 네트워크, 5xx, 해석 불가, 알 수 없는 오류 코드처럼 상대 쪽 실패
 * 요청 URL에 승인키가 들어가므로 cause는 보관하지 않는다.
 */
public class AddressLookupUnavailableException extends BusinessException {
	private AddressLookupUnavailableException(ErrorCode errorCode) {
		super(errorCode);
	}

	public static AddressLookupUnavailableException notConfigured() {
		return new AddressLookupUnavailableException(ErrorCode.ADDRESS_LOOKUP_UNAVAILABLE);
	}

	public static AddressLookupUnavailableException upstreamFailed() {
		return new AddressLookupUnavailableException(ErrorCode.ADDRESS_LOOKUP_FAILED);
	}
}
