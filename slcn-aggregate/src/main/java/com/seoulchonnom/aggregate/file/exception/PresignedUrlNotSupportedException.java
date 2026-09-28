package com.seoulchonnom.aggregate.file.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

/**
 * 로컬 디스크 프로바이더처럼 서명 URL을 만들 수 없는 저장소에서, 서명 URL이 전제인 기능을 막는다.
 */
public class PresignedUrlNotSupportedException extends BusinessException {
	public PresignedUrlNotSupportedException() {
		super(ErrorCode.PRESIGNED_URL_NOT_SUPPORTED);
	}
}
