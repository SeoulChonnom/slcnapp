package com.seoulchonnom.aggregate.client.exception;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class InvalidClientException extends BadRequestException {
	public InvalidClientException() {
		super(ErrorCode.INVALID_CLIENT);
	}
}
