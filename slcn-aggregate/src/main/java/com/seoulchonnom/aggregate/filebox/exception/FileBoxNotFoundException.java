package com.seoulchonnom.aggregate.filebox.exception;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;

public class FileBoxNotFoundException extends BadRequestException {
	public FileBoxNotFoundException() {
		super("FileBox를 찾을 수 없습니다.");
	}
}
