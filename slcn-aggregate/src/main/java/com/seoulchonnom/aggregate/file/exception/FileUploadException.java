package com.seoulchonnom.aggregate.file.exception;

import static com.seoulchonnom.spec.file.constant.FileConstant.*;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;

public class FileUploadException extends BadRequestException {
	public FileUploadException() {
		super(FILE_UPLOAD_ERROR_MESSAGE);
	}
}