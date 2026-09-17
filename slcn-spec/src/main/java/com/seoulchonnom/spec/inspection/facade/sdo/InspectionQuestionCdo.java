package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.List;

import com.seoulchonnom.spec.inspection.entity.vo.QuestionAnswerType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 등록과 동시에 v1 버전을 만든다. answerType은 여기서만 정할 수 있다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InspectionQuestionCdo {
	private QuestionAnswerType answerType;
	private String content;
	private String description;
	private List<QuestionChoiceSdo> choices;
	private String unit;
	private boolean required;
	private int sortOrder;
}
