package com.seoulchonnom.aggregate.file.exception;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class FilePathInvalidException extends BadRequestException {
	public FilePathInvalidException() {
		super(ErrorCode.FILE_PATH_INVALID);
	}
}
