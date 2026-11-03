package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

/**
 * 주소는 검색되지만 행안부 좌표제공 API가 좌표를 주지 않을 때의 400. 상대 장애가 아니므로 502가 아니다.
 * 사용자는 위치 없이 매물을 저장할 수 있다.
 */
public class AddressCoordinateNotFoundException extends BusinessException {
	public AddressCoordinateNotFoundException() {
		super(ErrorCode.ADDRESS_COORDINATE_NOT_FOUND);
	}
}
