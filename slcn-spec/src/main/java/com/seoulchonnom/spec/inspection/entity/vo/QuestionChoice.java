package com.seoulchonnom.spec.inspection.entity.vo;

import com.seoulchonnom.spec.common.entity.vo.JsonSerializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class QuestionChoice implements JsonSerializable {
	/**
	 * 답변이 참조하는 불변 식별자. label이 바뀌어도 과거 답변이 가리키는 대상은 유지된다.
	 */
	private String code;
	private String label;
	private int sortOrder;
}
