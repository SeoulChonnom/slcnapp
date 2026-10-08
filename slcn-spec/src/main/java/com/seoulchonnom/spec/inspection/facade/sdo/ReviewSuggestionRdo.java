package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI가 제안한 값. 어디에도 저장되지 않으며 FE가 폼을 채우고 사용자가 기존 PUT으로 저장한다.
 * 근거가 없는 필드는 null이 아니라 빈 문자열/빈 목록이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSuggestionRdo {
	private String oneLineReview;
	private String pros;
	private String cons;
	private List<String> tags;
}
