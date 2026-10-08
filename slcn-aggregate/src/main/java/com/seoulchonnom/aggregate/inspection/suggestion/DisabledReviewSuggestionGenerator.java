package com.seoulchonnom.aggregate.inspection.suggestion;

import com.seoulchonnom.aggregate.inspection.exception.ReviewSuggestionUnavailableException;

/**
 * API 키가 없는 환경(개발·테스트)의 대역. 호출하면 항상 사용 불가를 알린다.
 */
public class DisabledReviewSuggestionGenerator implements ReviewSuggestionGenerator {
	@Override
	public ReviewSuggestion generate(ReviewSuggestionPrompt prompt) {
		throw ReviewSuggestionUnavailableException.misconfigured(null);
	}
}
