package com.seoulchonnom.aggregate.inspection.store.mapper;

import java.util.ArrayList;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.inspection.store.jpo.ViewedPropertyJpo;
import com.seoulchonnom.spec.inspection.entity.ViewedProperty;

@Component
public class ViewedPropertyJpoMapper {
	public ViewedPropertyJpo toJpo(ViewedProperty property) {
		ViewedPropertyJpo jpo = new ViewedPropertyJpo(
			property.getInspectionVisitId(),
			property.getComplexName(),
			property.getName(),
			property.getMemo(),
			property.getOneLineReview(),
			property.getPros(),
			property.getCons(),
			property.getInterestLevel(),
			property.getStatus(),
			property.getSortOrder(),
			property.getAnswers() == null ? new ArrayList<>() : new ArrayList<>(property.getAnswers()),
			property.getRequiredAnswerCount(),
			property.getUnansweredRequiredCount()
		);
		jpo.setId(property.getId());
		jpo.setEntityVersion(property.getEntityVersion());
		jpo.setRegisteredTime(property.getRegisteredTime());
		jpo.setModifiedTime(property.getModifiedTime());
		return jpo;
	}

	public ViewedProperty toDomain(ViewedPropertyJpo jpo) {
		ViewedProperty property = ViewedProperty.builder()
			.inspectionVisitId(jpo.getInspectionVisitId())
			.complexName(jpo.getComplexName())
			.name(jpo.getName())
			.memo(jpo.getMemo())
			.oneLineReview(jpo.getOneLineReview())
			.pros(jpo.getPros())
			.cons(jpo.getCons())
			.interestLevel(jpo.getInterestLevel())
			.status(jpo.getStatus())
			.sortOrder(jpo.getSortOrder())
			.answers(jpo.getAnswers() == null ? new ArrayList<>() : new ArrayList<>(jpo.getAnswers()))
			.requiredAnswerCount(jpo.getRequiredAnswerCount())
			.unansweredRequiredCount(jpo.getUnansweredRequiredCount())
			.build();
		property.setId(jpo.getId());
		property.setEntityVersion(jpo.getEntityVersion());
		if (jpo.getRegisteredTime() != null) {
			property.setRegisteredTime(jpo.getRegisteredTime());
		}
		if (jpo.getModifiedTime() != null) {
			property.setModifiedTime(jpo.getModifiedTime());
		}
		return property;
	}
}
