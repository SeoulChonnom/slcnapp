package com.seoulchonnom.spec.inspection.entity.vo;

import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.common.entity.vo.JsonSerializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 질문 문구의 한 판본. InspectionQuestion.versions에 실려 inspection_question 행 안에 저장된다.
 *
 * 한 번 리스트에 들어가면 수정하지 않는다 — 고치면 그 문구로 답한 기록의 의미가 바뀐다.
 * 문구를 바꿀 때는 versionNo를 올린 새 항목을 뒤에 추가한다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class QuestionVersion implements JsonSerializable {
	private int versionNo;
	private String content;
	private String description;
	@Builder.Default
	private List<QuestionChoice> choices = new ArrayList<>();
	/**
	 * NUMBER에서만 쓴다. 예: 만원, m2
	 */
	private String unit;
}
