package com.seoulchonnom.aggregate.file.exception;

import static com.seoulchonnom.spec.file.constant.FileConstant.*;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;

public class FileExtException extends BadRequestException {
	public FileExtException() {
		super(FILE_EXT_ERROR_MESSAGE);
	}
}