package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

/**
 * 도보 경로를 얻지 못했을 때의 예외. 원인에 따라 상태가 다르다.
 * - 503(WALKING_ROUTE_UNAVAILABLE): 키 미설정, 거절된 키처럼 관리자가 고쳐야 하는 경우
 * - 429(WALKING_ROUTE_QUOTA_EXCEEDED): 상대의 일일 한도 초과
 * - 502(WALKING_ROUTE_FAILED): 타임아웃, 네트워크, 5xx, 해석 불가, 경로 없음 등 상대 쪽 실패
 * 카카오 오류 본문에 REST 키가 그대로 들어오므로 cause와 상대 메시지는 보관하지 않는다.
 */
public class WalkingRouteException extends BusinessException {
	private WalkingRouteException(ErrorCode errorCode) {
		super(errorCode);
	}

	public static WalkingRouteException notConfigured() {
		return new WalkingRouteException(ErrorCode.WALKING_ROUTE_UNAVAILABLE);
	}

	public static WalkingRouteException quotaExceeded() {
		return new WalkingRouteException(ErrorCode.WALKING_ROUTE_QUOTA_EXCEEDED);
	}

	public static WalkingRouteException upstreamFailed() {
		return new WalkingRouteException(ErrorCode.WALKING_ROUTE_FAILED);
	}
}
