package com.seoulchonnom.spec.inspection.mapper;

import org.springframework.stereotype.Component;

import com.seoulchonnom.spec.inspection.entity.InspectionQuestionCategory;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryRdo;

@Component
public class InspectionQuestionCategoryMapper {
	public InspectionQuestionCategory toInspectionQuestionCategory(String id, InspectionQuestionCategoryCdo cdo) {
		return new InspectionQuestionCategory(id, cdo.getName(), cdo.getSortOrder());
	}

	/**
	 * @param enabledQuestionCount 이 분류에 속한 활성 질문 수. 엔티티는 이를 모르므로 호출자가 집계해 넘긴다
	 */
	public InspectionQuestionCategoryRdo toInspectionQuestionCategoryRdo(InspectionQuestionCategory category,
		int enabledQuestionCount) {
		InspectionQuestionCategoryRdo rdo = new InspectionQuestionCategoryRdo();
		rdo.setCategoryId(category.getId());
		rdo.setName(category.getName());
		rdo.setSortOrder(category.getSortOrder());
		rdo.setEnabled(category.isEnabled());
		rdo.setEnabledQuestionCount(enabledQuestionCount);
		return rdo;
	}
}
