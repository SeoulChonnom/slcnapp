package com.seoulchonnom.aggregate.house.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class HouseComplexNotFoundException extends BusinessException {
	public HouseComplexNotFoundException() {
		super(ErrorCode.HOUSE_COMPLEX_NOT_FOUND);
	}

	public HouseComplexNotFoundException(String message) {
		super(ErrorCode.HOUSE_COMPLEX_NOT_FOUND, message);
	}
}
