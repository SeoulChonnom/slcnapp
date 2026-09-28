package com.seoulchonnom.aggregate.file.exception;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class FileExtException extends BadRequestException {
	public FileExtException() {
		super(ErrorCode.FILE_EXT_INVALID);
	}

	public FileExtException(String message) {
		super(ErrorCode.FILE_EXT_INVALID, message);
	}
}
