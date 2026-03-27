package com.seoulchonnom.aggregate.common.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class UnsupportedMediaTypeException extends BusinessException {
	public UnsupportedMediaTypeException() {
		super(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
	}

	public UnsupportedMediaTypeException(String message) {
		super(ErrorCode.UNSUPPORTED_MEDIA_TYPE, message);
	}
}
