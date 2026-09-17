package com.seoulchonnom.spec.inspection.entity.vo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.common.entity.vo.JsonSerializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 매물 생성 시점의 활성 질문을 그대로 복사해 만든 답변 한 건.
 * ViewedProperty.answers에 실려 viewed_property 행 안에 저장된다.
 *
 * 행 ID가 없다. questionId가 리스트 안의 식별자이며, 저장 API도 questionId로 대상을 찾는다.
 *
 * 문구/타입/필수 여부/단위를 스냅샷으로 들고 있는 것이 이 설계의 핵심이다.
 * 완료 검증도 과거 기록 렌더링도 질문 마스터의 현재 상태를 보지 않는다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class PropertyAnswer implements JsonSerializable {
	private String questionId;
	private int questionVersionNo;

	private String questionContent;
	private String questionDescription;
	private QuestionAnswerType answerType;
	private boolean required;
	private int sortOrder;
	/**
	 * NUMBER의 단위 스냅샷. 빠뜨리면 과거 답변이 값만 남고 "만원"인지 "m2"인지 잃는다.
	 * 단위 변경은 새 버전을 만드는 변경이라 마스터를 되짚어 복원할 수도 없다.
	 */
	private String unit;
	@Builder.Default
	private List<QuestionChoice> choiceOptions = new ArrayList<>();

	private String textValue;
	private Boolean booleanValue;
	private BigDecimal numberValue;
	private Integer ratingValue;
	@Builder.Default
	private List<String> selectedCodes = new ArrayList<>();

	private boolean answered;

	/**
	 * 값이 바뀔 때마다 다시 계산한다. 판정 규칙을 한 곳에만 둔다.
	 */
	public void refreshAnswered() {
		this.answered = resolveAnswered();
	}

	private boolean resolveAnswered() {
		if (answerType == null) {
			return false;
		}
		return switch (answerType) {
			case TEXT, LONG_TEXT -> textValue != null && !textValue.isBlank();
			case BOOLEAN -> booleanValue != null;
			case NUMBER -> numberValue != null;
			case RATING -> ratingValue != null;
			case SINGLE_SELECT -> selectedCodes != null && selectedCodes.size() == 1;
			case MULTI_SELECT -> selectedCodes != null && !selectedCodes.isEmpty();
		};
	}
}
