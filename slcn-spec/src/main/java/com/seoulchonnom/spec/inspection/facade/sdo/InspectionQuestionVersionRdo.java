package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 버전 이력 한 행. answerCount는 그 버전의 문구로 답한 기록 수다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InspectionQuestionVersionRdo {
	private String questionId;
	private int versionNo;
	private String content;
	private String description;
	private List<QuestionChoiceSdo> choices = new ArrayList<>();
	private String unit;
	private Integer answerCount;
	private boolean current;
}
