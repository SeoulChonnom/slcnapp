package com.seoulchonnom.spec.inspection.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryStatusUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryUdo;

/**
 * 질문 대분류 관리. 쓰기는 ADMIN, 조회는 USER — 매물 문답 화면이 분류별 섹션을 그리려면
 * 일반 사용자도 목록을 읽어야 한다. 물리 삭제 경로는 두지 않는다.
 */
public interface InspectionQuestionCategoryFacade {
	ResponseEntity<List<InspectionQuestionCategoryRdo>> getInspectionQuestionCategories(boolean includeDisabled);

	/**
	 * 비활성 분류까지 포함해 전체에서 이름 중복이면 409다(계획 §0-6).
	 */
	ResponseEntity<InspectionQuestionCategoryRdo> registerInspectionQuestionCategory(
		InspectionQuestionCategoryCdo inspectionQuestionCategoryCdo);

	ResponseEntity<InspectionQuestionCategoryRdo> renameInspectionQuestionCategory(String categoryId,
		InspectionQuestionCategoryUdo inspectionQuestionCategoryUdo);

	/**
	 * 활성 질문이 하나라도 있는 분류는 비활성화할 수 없다(409, 계획 §0-3).
	 */
	ResponseEntity<InspectionQuestionCategoryRdo> changeInspectionQuestionCategoryStatus(String categoryId,
		InspectionQuestionCategoryStatusUdo inspectionQuestionCategoryStatusUdo);

	/**
	 * 요청에 빠진 분류는 기존 순서를 유지한다.
	 */
	ResponseEntity<Void> modifyInspectionQuestionCategoryOrder(List<InspectionQuestionCategoryOrderUdo> orders);
}
