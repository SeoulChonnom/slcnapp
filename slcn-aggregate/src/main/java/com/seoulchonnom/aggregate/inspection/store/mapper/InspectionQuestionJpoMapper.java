package com.seoulchonnom.aggregate.inspection.store.mapper;

import java.util.ArrayList;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionQuestionJpo;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestion;

@Component
public class InspectionQuestionJpoMapper {
	public InspectionQuestionJpo toJpo(InspectionQuestion question) {
		InspectionQuestionJpo jpo = new InspectionQuestionJpo(
			question.getAnswerType(),
			question.isRequired(),
			question.getSortOrder(),
			question.isEnabled(),
			question.getVersions() == null ? new ArrayList<>() : new ArrayList<>(question.getVersions()),
			question.getCurrentVersionNo()
		);
		jpo.setId(question.getId());
		jpo.setEntityVersion(question.getEntityVersion());
		jpo.setRegisteredTime(question.getRegisteredTime());
		jpo.setModifiedTime(question.getModifiedTime());
		return jpo;
	}

	public InspectionQuestion toDomain(InspectionQuestionJpo jpo) {
		InspectionQuestion question = InspectionQuestion.builder()
			.answerType(jpo.getAnswerType())
			.required(jpo.isRequired())
			.sortOrder(jpo.getSortOrder())
			.enabled(jpo.isEnabled())
			.versions(jpo.getVersions() == null ? new ArrayList<>() : new ArrayList<>(jpo.getVersions()))
			.currentVersionNo(jpo.getCurrentVersionNo())
			.build();
		question.setId(jpo.getId());
		question.setEntityVersion(jpo.getEntityVersion());
		if (jpo.getRegisteredTime() != null) {
			question.setRegisteredTime(jpo.getRegisteredTime());
		}
		if (jpo.getModifiedTime() != null) {
			question.setModifiedTime(jpo.getModifiedTime());
		}
		return question;
	}
}
