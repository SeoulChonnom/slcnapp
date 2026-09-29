package com.seoulchonnom.spec.common.response;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.common.exception.ErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모든 에러 응답의 공통 본문이다. 비즈니스 예외, Spring MVC 예외, 보안(401/403)이 같은 형태로 나간다.
 *
 * FE는 code로 분기하고 title은 그대로 보여 준다. title은 사람이 읽는 문장이라 바뀔 수 있으므로
 * 파싱하지 않는다. status는 HTTP 상태와 같은 값을 본문에도 싣는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
	private String title;
	private int status;
	private String code;
	/**
	 * 필드 단위 검증 실패일 때만 채운다. 그 밖의 에러는 빈 배열이다 - FE가 null 검사 없이 순회할 수 있게 한다.
	 */
	private List<Violation> errors = new ArrayList<>();

	public static ErrorResponse of(ErrorCode errorCode) {
		return of(errorCode, errorCode.getMessage());
	}

	public static ErrorResponse of(ErrorCode errorCode, String title) {
		return of(errorCode, title, List.of());
	}

	public static ErrorResponse of(ErrorCode errorCode, String title, List<Violation> errors) {
		return new ErrorResponse(title, errorCode.getHttpStatus().value(), errorCode.name(),
			new ArrayList<>(errors));
	}

	/**
	 * @param field   요청 필드 또는 파라미터 이름
	 * @param code    위반 종류. NOT_BLANK, SIZE, TYPE_MISMATCH처럼 대문자 스네이크 케이스다
	 * @param message 사람이 읽는 설명
	 */
	public record Violation(String field, String code, String message) {
	}
}
