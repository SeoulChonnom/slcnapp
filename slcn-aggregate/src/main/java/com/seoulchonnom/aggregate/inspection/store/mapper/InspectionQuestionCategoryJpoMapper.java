package com.seoulchonnom.aggregate.inspection.store.mapper;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.inspection.store.jpo.InspectionQuestionCategoryJpo;
import com.seoulchonnom.spec.inspection.entity.InspectionQuestionCategory;

@Component
public class InspectionQuestionCategoryJpoMapper {
	public InspectionQuestionCategoryJpo toJpo(InspectionQuestionCategory category) {
		InspectionQuestionCategoryJpo jpo = new InspectionQuestionCategoryJpo(
			category.getName(),
			category.getSortOrder(),
			category.isEnabled()
		);
		jpo.setId(category.getId());
		jpo.setEntityVersion(category.getEntityVersion());
		jpo.setRegisteredTime(category.getRegisteredTime());
		jpo.setModifiedTime(category.getModifiedTime());
		return jpo;
	}

	public InspectionQuestionCategory toDomain(InspectionQuestionCategoryJpo jpo) {
		InspectionQuestionCategory category = InspectionQuestionCategory.builder()
			.name(jpo.getName())
			.sortOrder(jpo.getSortOrder())
			.enabled(jpo.isEnabled())
			.build();
		category.setId(jpo.getId());
		category.setEntityVersion(jpo.getEntityVersion());
		if (jpo.getRegisteredTime() != null) {
			category.setRegisteredTime(jpo.getRegisteredTime());
		}
		if (jpo.getModifiedTime() != null) {
			category.setModifiedTime(jpo.getModifiedTime());
		}
		return category;
	}
}
