package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 질문 마스터 + 현재 버전의 문구를 합쳐 내린다.
 * answerCount는 withAnswerCount=true일 때만 채운다 — 목록 조회가 답변 집계를 항상 끌고 가지 않게 한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InspectionQuestionRdo {
	private String questionId;
	private QuestionAnswerType answerType;
	private boolean required;
	private int sortOrder;
	private boolean enabled;
	private int currentVersionNo;
	private String content;
	private String description;
	private List<QuestionChoiceSdo> choices = new ArrayList<>();
	private String unit;
	private Integer answerCount;
	/**
	 * 낙관적 잠금 대조 키. 수정 요청은 이 값을 그대로 되돌려 보내야 한다(C-1).
	 * currentVersionNo는 문구 수정에만 오르고 policy/status 변경에는 안 올라서 충돌 키로 못 쓴다.
	 */
	private long entityVersion;
}
