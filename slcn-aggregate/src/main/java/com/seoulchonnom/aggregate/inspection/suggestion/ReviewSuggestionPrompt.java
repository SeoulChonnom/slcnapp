package com.seoulchonnom.aggregate.inspection.suggestion;

/**
 * 모델에 보낼 입력. 지시문과 사용자 기록을 나눠 두는 것은 기록 안의 문장이 지시로 읽히지 않게 하기 위해서다.
 */
public record ReviewSuggestionPrompt(String instruction, String content) {
}
