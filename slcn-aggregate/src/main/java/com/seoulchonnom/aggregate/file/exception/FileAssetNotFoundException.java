package com.seoulchonnom.aggregate.file.exception;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

public class FileAssetNotFoundException extends BadRequestException {
	public FileAssetNotFoundException() {
		super(ErrorCode.FILE_ASSET_NOT_FOUND);
	}
}
