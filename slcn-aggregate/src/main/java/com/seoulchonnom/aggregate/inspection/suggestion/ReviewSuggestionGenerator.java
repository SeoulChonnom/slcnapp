package com.seoulchonnom.aggregate.inspection.suggestion;

import com.seoulchonnom.aggregate.inspection.exception.ReviewSuggestionUnavailableException;

/**
 * 후기 제안 생성 포트. 구현체가 어떤 모델/SDK를 쓰는지는 도메인이 모른다.
 */
public interface ReviewSuggestionGenerator {
	/**
	 * @throws ReviewSuggestionUnavailableException 설정이 없거나 호출/응답 해석에 실패했을 때
	 */
	ReviewSuggestion generate(ReviewSuggestionPrompt prompt);
}
