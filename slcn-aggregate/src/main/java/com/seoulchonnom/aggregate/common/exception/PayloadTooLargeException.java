package com.seoulchonnom.aggregate.common.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class PayloadTooLargeException extends BusinessException {
	public PayloadTooLargeException() {
		super(ErrorCode.PAYLOAD_TOO_LARGE);
	}

	public PayloadTooLargeException(String message) {
		super(ErrorCode.PAYLOAD_TOO_LARGE, message);
	}
}
