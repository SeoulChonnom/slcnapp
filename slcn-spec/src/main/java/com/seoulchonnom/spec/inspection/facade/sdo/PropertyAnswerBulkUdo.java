package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 요청에 없는 questionId의 답변 행은 그대로 둔다(부분 저장).
 */
@Getter
@Setter
@NoArgsConstructor
public class PropertyAnswerBulkUdo {
	private List<PropertyAnswerUdo> answers = new ArrayList<>();
}
