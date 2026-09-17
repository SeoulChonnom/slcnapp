package com.seoulchonnom.spec.inspection.facade.sdo;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 답변 값만 갱신한다. 질문 스냅샷은 매물 생성 시 고정되었으므로 요청으로 바꿀 수 없다.
 */
@Getter
@Setter
@NoArgsConstructor
public class PropertyAnswerUdo {
	private String questionId;
	private String textValue;
	private Boolean booleanValue;
	private BigDecimal numberValue;
	private Integer ratingValue;
	private List<String> selectedCodes;
}
