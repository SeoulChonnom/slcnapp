package com.seoulchonnom.spec.inspection.mapper;

import org.springframework.stereotype.Component;

import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;
import com.seoulchonnom.spec.inspection.entity.vo.QuestionVersion;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionContentUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionVersionRdo;

@Component
public class InspectionQuestionMapper {
	/**
	 * 등록과 동시에 v1을 붙인다. answerType은 이 경로에서만 정할 수 있다.
	 */
	public InspectionQuestion toInspectionQuestion(String id, InspectionQuestionCdo cdo) {
		InspectionQuestion question = new InspectionQuestion(id, cdo.getAnswerType(), cdo.isRequired(),
			cdo.getSortOrder());
		question.addVersion(cdo.getContent(), cdo.getDescription(),
			PropertyAnswerMapper.toQuestionChoices(cdo.getChoices()), cdo.getUnit());
		return question;
	}

	/**
	 * 문구 수정은 기존 버전을 고치지 않고 다음 번호의 버전을 뒤에 추가한다.
	 * answerType은 받지 않는다 — 생성 후 변경은 금지다.
	 */
	public QuestionVersion addVersion(InspectionQuestion question, InspectionQuestionContentUdo udo) {
		return question.addVersion(udo.getContent(), udo.getDescription(),
			PropertyAnswerMapper.toQuestionChoices(udo.getChoices()), udo.getUnit());
	}

	/**
	 * @param answerCount withAnswerCount 요청일 때만 채우고, 아니면 null
	 */
	public InspectionQuestionRdo toInspectionQuestionRdo(InspectionQuestion question, Integer answerCount) {
		InspectionQuestionRdo rdo = new InspectionQuestionRdo();
		rdo.setQuestionId(question.getId());
		rdo.setAnswerType(question.getAnswerType());
		rdo.setRequired(question.isRequired());
		rdo.setSortOrder(question.getSortOrder());
		rdo.setEnabled(question.isEnabled());
		rdo.setCurrentVersionNo(question.getCurrentVersionNo());
		rdo.setEntityVersion(question.getEntityVersion());
		question.currentVersion().ifPresent(version -> {
			rdo.setContent(version.getContent());
			rdo.setDescription(version.getDescription());
			rdo.setChoices(PropertyAnswerMapper.toQuestionChoiceSdos(version.getChoices()));
			rdo.setUnit(version.getUnit());
		});
		rdo.setAnswerCount(answerCount);
		return rdo;
	}

	public InspectionQuestionVersionRdo toInspectionQuestionVersionRdo(InspectionQuestion question,
		QuestionVersion version, Integer answerCount) {
		InspectionQuestionVersionRdo rdo = new InspectionQuestionVersionRdo();
		rdo.setQuestionId(question.getId());
		rdo.setVersionNo(version.getVersionNo());
		rdo.setContent(version.getContent());
		rdo.setDescription(version.getDescription());
		rdo.setChoices(PropertyAnswerMapper.toQuestionChoiceSdos(version.getChoices()));
		rdo.setUnit(version.getUnit());
		rdo.setAnswerCount(answerCount);
		rdo.setCurrent(version.getVersionNo() == question.getCurrentVersionNo());
		return rdo;
	}
}
