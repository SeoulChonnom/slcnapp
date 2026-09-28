package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnansweredQuestionRdo {
	private String questionId;
	private String question;
	private int sortOrder;
	/**
	 * 답변 스냅샷의 분류명이다. 백필 전 과거 매물은 null("미분류")일 수 있다.
	 */
	private String categoryName;
}
