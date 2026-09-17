package com.seoulchonnom.spec.inspection.entity.vo;

/**
 * 질문 생성 시점에 확정되고 이후 변경할 수 없다. 답변 값 컬럼이 타입별로 나뉘어 있어
 * 타입을 바꾸면 이미 쌓인 답변을 한 축으로 읽을 방법이 없다.
 */
public enum QuestionAnswerType {
	TEXT,
	LONG_TEXT,
	BOOLEAN,
	SINGLE_SELECT,
	MULTI_SELECT,
	NUMBER,
	RATING
}
