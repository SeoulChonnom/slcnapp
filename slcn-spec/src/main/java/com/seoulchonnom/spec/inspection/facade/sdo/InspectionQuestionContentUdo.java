package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 문구/설명/선택지/단위 수정. 새 버전을 만든다 — 기존 답변은 자기 스냅샷을 그대로 본다.
 * answerType은 포함하지 않는다. 생성 후 변경은 금지다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InspectionQuestionContentUdo {
	private String content;
	private String description;
	private List<QuestionChoiceSdo> choices;
	private String unit;
}
