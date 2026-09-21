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
	/**
	 * 조회 시 받은 entityVersion을 그대로 되돌려 보내야 한다(C-1).
	 * 두 관리자가 같은 질문을 동시에 열어 두고 순서대로 저장하는 lost update를 막는 대조 키다.
	 * null이면 프런트가 대조 키 없이 보낸 것이므로 400으로 거절한다 — 선택 파라미터로 두면
	 * 안 보내는 것만으로 검사를 우회할 수 있어 목적 자체가 무너진다.
	 */
	private Long entityVersion;
}
