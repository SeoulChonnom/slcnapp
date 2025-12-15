package com.seoulchonnom.aggregate.file.exception;

import static com.seoulchonnom.spec.file.constant.FileConstant.*;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;

public class FilePathInvalidException extends BadRequestException {
	public FilePathInvalidException() {
		super(FILE_PATH_INVALID_ERROR_MESSAGE);
	}
}