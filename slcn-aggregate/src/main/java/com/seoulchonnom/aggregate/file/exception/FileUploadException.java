package com.seoulchonnom.aggregate.file.exception;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class FileUploadException extends BadRequestException {
	public FileUploadException() {
		super(ErrorCode.FILE_UPLOAD_FAILED);
	}
}
