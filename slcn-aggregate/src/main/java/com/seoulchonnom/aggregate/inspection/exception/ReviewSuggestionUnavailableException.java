package com.seoulchonnom.aggregate.inspection.exception;

import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

import static com.seoulchonnom.spec.inspection.constant.InspectionConstant.*;

/**
 * 후기 제안을 쓸 수 없을 때의 503. 코드는 하나이고 title만 원인에 따라 다르다 —
 * 일시적 실패는 "잠시 후 다시", 설정 문제는 "관리자에게 문의"로 안내한다.
 * 응답 문구는 고정이고 원인(SDK/Gemini 오류 문구)은 cause로만 보관해 클라이언트에 내보내지 않는다.
 */
public class ReviewSuggestionUnavailableException extends BusinessException {
	private ReviewSuggestionUnavailableException(String message, Throwable cause) {
		super(ErrorCode.REVIEW_SUGGESTION_UNAVAILABLE, message);
		if (cause != null) {
			initCause(cause);
		}
	}

	/**
	 * 429/5xx, 네트워크·타임아웃, 응답 해석 불가처럼 시간이 지나면 풀릴 수 있는 실패.
	 */
	public static ReviewSuggestionUnavailableException transientFailure(Throwable cause) {
		return new ReviewSuggestionUnavailableException(ErrorCode.REVIEW_SUGGESTION_UNAVAILABLE.getMessage(), cause);
	}

	/**
	 * 키 미설정, 잘못된 키·모델 ID처럼 관리자가 고쳐야 하는 실패.
	 */
	public static ReviewSuggestionUnavailableException misconfigured(Throwable cause) {
		return new ReviewSuggestionUnavailableException(REVIEW_SUGGESTION_MISCONFIGURED_ERROR_MESSAGE, cause);
	}
}
