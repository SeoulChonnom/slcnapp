package com.seoulchonnom.aggregate.file.exception;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class FileSizeException extends BadRequestException {
	public FileSizeException() {
		super(ErrorCode.FILE_SIZE_EXCEEDED);
	}
}
