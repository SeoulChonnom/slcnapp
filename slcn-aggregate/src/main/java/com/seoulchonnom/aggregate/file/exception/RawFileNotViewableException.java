package com.seoulchonnom.aggregate.file.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

/**
 * RAW 첨부를 인라인 이미지로 요청했다. img 태그에 물리면 수십 MB가 내려가므로 없는 이미지처럼 404로 막는다.
 */
public class RawFileNotViewableException extends BusinessException {
	public RawFileNotViewableException() {
		super(ErrorCode.FILE_ASSET_RAW_NOT_VIEWABLE);
	}
}
