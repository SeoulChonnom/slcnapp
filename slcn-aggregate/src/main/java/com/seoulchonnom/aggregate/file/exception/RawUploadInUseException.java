package com.seoulchonnom.aggregate.file.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class RawUploadInUseException extends BusinessException {
	public RawUploadInUseException() {
		super(ErrorCode.RAW_UPLOAD_IN_USE);
	}
}
