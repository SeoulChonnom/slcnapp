package com.seoulchonnom.aggregate.inspection.suggestion;

import java.util.List;

/**
 * 생성기가 돌려주는 원본 제안. 길이/중복 정규화 전의 값이라 null이 섞여 있을 수 있다.
 */
public record ReviewSuggestion(String oneLineReview, String cons, List<String> tags) {
}
