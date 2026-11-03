package com.seoulchonnom.aggregate.house.collect;

/**
 * 건축물대장 조회 한 번의 결과. 실패는 예외가 아니라 outcome으로 돌려준다.
 *
 * @param summary FOUND일 때만 값이 있다.
 */
public record RegisterLookupResult(Outcome outcome, RegisterSummary summary) {

	public enum Outcome {
		/** 네 필드 중 하나 이상을 찾았다. */
		FOUND,
		/** 대장에 없거나 네 필드가 모두 비어 있다. 다시 해도 같으므로 즉시 FAILED다. */
		NOT_FOUND,
		/** 재시도 후에도 호출이 실패했다. 시도 횟수를 올리고 다음 날 다시 한다. */
		FAILED,
		/** 호출 예산이 없어 시작하지 못했거나 도중에 멈췄다. 단지 상태는 그대로 둔다. */
		BUDGET_EXHAUSTED,
		/** 일일 한도 초과. 단계를 멈춘다. */
		QUOTA_EXCEEDED,
		/** 인증 키 거절. 단계를 멈춘다. */
		KEY_REJECTED
	}

	public static RegisterLookupResult found(RegisterSummary summary) {
		return new RegisterLookupResult(Outcome.FOUND, summary);
	}

	public static RegisterLookupResult of(Outcome outcome) {
		return new RegisterLookupResult(outcome, null);
	}
}
