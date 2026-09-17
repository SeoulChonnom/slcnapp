package com.seoulchonnom.spec.inspection.facade.sdo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 질문 마스터를 조회하지 않고도 렌더링할 수 있어야 한다. 문구/타입/선택지는 전부 답변 행의 스냅샷에서 온다.
 *
 * isCurrentVersion과 questionEnabled만 현재 질문 마스터와 비교해 계산하며,
 * 이 둘은 배지 전용이다. 이 필드로 렌더링을 바꾸면 과거 기록이 그때 그대로라는 보장이 깨진다.
 * 질문 마스터 조회가 비어도 문답 렌더링은 정상 동작해야 하므로 null을 허용한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class PropertyAnswerRdo {
	private String questionId;
	private int questionVersionNo;
	private String question;
	private String description;
	private QuestionAnswerType answerType;
	private boolean required;
	private int sortOrder;
	/**
	 * NUMBER의 단위 스냅샷. 값만으로는 "만원"인지 "m2"인지 알 수 없다.
	 */
	private String unit;
	private boolean answered;
	private List<QuestionChoiceSdo> choiceOptions = new ArrayList<>();

	private String textValue;
	private Boolean booleanValue;
	private BigDecimal numberValue;
	private Integer ratingValue;
	private List<String> selectedCodes = new ArrayList<>();

	private Boolean isCurrentVersion;
	private Boolean questionEnabled;
}
