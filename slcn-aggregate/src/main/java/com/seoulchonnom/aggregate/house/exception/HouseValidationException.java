package com.seoulchonnom.aggregate.house.exception;

import java.util.List;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.common.response.ErrorResponse.Violation;

import lombok.Getter;

/**
 * 필드 단위 위반 목록이 필요한 입력 검증 실패(비교 items). 400 VALIDATION_FAILED이고,
 * 핸들러가 violations를 응답의 errors[]에 그대로 싣는다.
 */
@Getter
public class HouseValidationException extends BusinessException {
	private final List<Violation> violations;

	public HouseValidationException(String message, List<Violation> violations) {
		super(ErrorCode.VALIDATION_FAILED, message);
		this.violations = List.copyOf(violations);
	}
}
