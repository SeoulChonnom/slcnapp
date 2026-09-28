package com.seoulchonnom.spec.inspection.mapper;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.inspection.entity.InspectionQuestionCategory;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryRdo;

class InspectionQuestionCategoryMapperTest {
	private final InspectionQuestionCategoryMapper inspectionQuestionCategoryMapper =
		new InspectionQuestionCategoryMapper();

	@Test
	void toInspectionQuestionCategory_shouldStartEnabled() {
		InspectionQuestionCategory category = inspectionQuestionCategoryMapper.toInspectionQuestionCategory(
			"INSPECTION_QUESTION_CATEGORY-0001", new InspectionQuestionCategoryCdo("채광·환기", 1));

		assertThat(category.getId()).isEqualTo("INSPECTION_QUESTION_CATEGORY-0001");
		assertThat(category.getName()).isEqualTo("채광·환기");
		assertThat(category.getSortOrder()).isEqualTo(1);
		assertThat(category.isEnabled()).isTrue();
	}

	@Test
	void toInspectionQuestionCategoryRdo_shouldCarryEnabledQuestionCount() {
		InspectionQuestionCategory category = new InspectionQuestionCategory("INSPECTION_QUESTION_CATEGORY-0001",
			"채광·환기", 1);

		InspectionQuestionCategoryRdo rdo = inspectionQuestionCategoryMapper.toInspectionQuestionCategoryRdo(
			category, 4);

		assertThat(rdo.getCategoryId()).isEqualTo("INSPECTION_QUESTION_CATEGORY-0001");
		assertThat(rdo.getName()).isEqualTo("채광·환기");
		assertThat(rdo.getSortOrder()).isEqualTo(1);
		assertThat(rdo.isEnabled()).isTrue();
		assertThat(rdo.getEnabledQuestionCount()).isEqualTo(4);
	}
}
