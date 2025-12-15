package com.seoulchonnom.aggregate.file.exception;

import static com.seoulchonnom.spec.file.constant.FileConstant.*;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;

public class FileSizeException extends BadRequestException {
	public FileSizeException() {
		super(FILE_SIZE_ERROR_MESSAGE);
	}
}