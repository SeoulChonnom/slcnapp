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
	 * 답변 스냅샷의 분류명이다.
	 */
	private String categoryName;
}
