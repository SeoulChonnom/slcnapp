package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

/**
 * 행안부가 검색어를 거절했을 때의 400. 메시지는 행안부의 errorMessage를 그대로 쓴다(사용자가 고칠 수 있는 안내).
 */
public class InvalidAddressKeywordException extends BusinessException {
	public InvalidAddressKeywordException(String message) {
		super(ErrorCode.INVALID_ADDRESS_KEYWORD, message == null || message.isBlank()
			? ErrorCode.INVALID_ADDRESS_KEYWORD.getMessage() : message);
	}
}
