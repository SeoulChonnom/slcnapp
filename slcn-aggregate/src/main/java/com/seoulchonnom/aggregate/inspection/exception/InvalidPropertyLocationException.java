package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

/**
 * 매물 위치 요청이 올바르지 않을 때의 400(VALIDATION_FAILED).
 */
public class InvalidPropertyLocationException extends BusinessException {
	public InvalidPropertyLocationException(String message) {
		super(ErrorCode.VALIDATION_FAILED, message);
	}
}
