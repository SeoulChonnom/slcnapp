package com.seoulchonnom.spec.inspection.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.vo.PropertyAnswer;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionChoice;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionVersion;
import com.seoulchonnom.spec.inspection.facade.sdo.PropertyAnswerRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.QuestionChoiceSdo;

@Component
public class PropertyAnswerMapper {
	/**
	 * 매물 생성 시점의 질문을 그대로 복사해 답변을 만든다.
	 * 여기서 복사한 값이 이후 질문 마스터가 바뀌어도 그 매물의 기준으로 남는다.
	 */
	public PropertyAnswer toPropertyAnswer(InspectionQuestion question, QuestionVersion version) {
		PropertyAnswer answer = new PropertyAnswer();
		answer.setQuestionId(question.getId());
		answer.setQuestionVersionNo(version.getVersionNo());
		answer.setQuestionContent(version.getContent());
		answer.setQuestionDescription(version.getDescription());
		answer.setAnswerType(question.getAnswerType());
		answer.setRequired(question.isRequired());
		answer.setSortOrder(question.getSortOrder());
		answer.setUnit(version.getUnit());
		answer.setChoiceOptions(version.getChoices() == null ? new ArrayList<>()
			: new ArrayList<>(version.getChoices()));
		answer.setSelectedCodes(new ArrayList<>());
		answer.setAnswered(false);
		return answer;
	}

	/**
	 * 문구/타입/단위/선택지는 어떤 경우에도 답변의 스냅샷을 쓴다.
	 *
	 * question은 isCurrentVersion/questionEnabled 배지 계산에만 쓰고, null이면 두 필드를 비워 둔다.
	 * 이 두 필드로 렌더링을 바꾸면 "질문 내용 변경으로 기존 기록이 변경되어서는 안 된다"는 요구가 깨진다.
	 */
	public PropertyAnswerRdo toPropertyAnswerRdo(PropertyAnswer answer, InspectionQuestion question) {
		PropertyAnswerRdo rdo = new PropertyAnswerRdo();
		rdo.setQuestionId(answer.getQuestionId());
		rdo.setQuestionVersionNo(answer.getQuestionVersionNo());
		rdo.setQuestion(answer.getQuestionContent());
		rdo.setDescription(answer.getQuestionDescription());
		rdo.setAnswerType(answer.getAnswerType());
		rdo.setRequired(answer.isRequired());
		rdo.setSortOrder(answer.getSortOrder());
		rdo.setUnit(answer.getUnit());
		rdo.setAnswered(answer.isAnswered());
		rdo.setChoiceOptions(toQuestionChoiceSdos(answer.getChoiceOptions()));
		rdo.setTextValue(answer.getTextValue());
		rdo.setBooleanValue(answer.getBooleanValue());
		rdo.setNumberValue(answer.getNumberValue());
		rdo.setRatingValue(answer.getRatingValue());
		rdo.setSelectedCodes(answer.getSelectedCodes() == null ? new ArrayList<>()
			: new ArrayList<>(answer.getSelectedCodes()));
		if (question != null) {
			rdo.setIsCurrentVersion(answer.getQuestionVersionNo() == question.getCurrentVersionNo());
			rdo.setQuestionEnabled(question.isEnabled());
		}
		return rdo;
	}

	static List<QuestionChoiceSdo> toQuestionChoiceSdos(List<QuestionChoice> choices) {
		if (choices == null) {
			return new ArrayList<>();
		}
		return choices.stream()
			.map(choice -> new QuestionChoiceSdo(choice.getCode(), choice.getLabel(), choice.getSortOrder()))
			.collect(Collectors.toCollection(ArrayList::new));
	}

	static List<QuestionChoice> toQuestionChoices(List<QuestionChoiceSdo> sdos) {
		if (sdos == null) {
			return new ArrayList<>();
		}
		return sdos.stream()
			.map(sdo -> new QuestionChoice(sdo.getCode(), sdo.getLabel(), sdo.getSortOrder()))
			.collect(Collectors.toCollection(ArrayList::new));
	}
}
