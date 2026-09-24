package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 선택지 입력과 출력에 함께 쓴다. 생성 요청과 수정 요청 양쪽에 중첩되므로 Cdo/Udo 어느 쪽도 맞지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuestionChoiceSdo {
	private String code;
	private String label;
	private int sortOrder;
}
