package com.seoulchonnom.rest.inspection;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.inspection.logic.InspectionQuestionCategoryLogic;
import com.seoulchonnom.spec.inspection.facade.InspectionQuestionCategoryFacade;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryStatusUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryUdo;

import lombok.RequiredArgsConstructor;

/**
 * 쓰기는 ADMIN, 조회는 USER다. 권한 분리는 SecurityConfiguration의 메서드별 matcher가 맡는다.
 * 매물 문답 화면이 분류별 섹션을 그리려면 일반 사용자도 목록을 읽어야 한다.
 */
@RestController
@RequestMapping("/inspection-question-categories")
@RequiredArgsConstructor
public class InspectionQuestionCategoryResource implements InspectionQuestionCategoryFacade {
	private final InspectionQuestionCategoryLogic inspectionQuestionCategoryLogic;

	@Override
	@GetMapping
	public ResponseEntity<List<InspectionQuestionCategoryRdo>> getInspectionQuestionCategories(
		@RequestParam(value = "includeDisabled", defaultValue = "false") boolean includeDisabled) {
		return new ResponseEntity<>(
			inspectionQuestionCategoryLogic.getInspectionQuestionCategories(includeDisabled), HttpStatus.OK);
	}

	@Override
	@PostMapping
	public ResponseEntity<InspectionQuestionCategoryRdo> registerInspectionQuestionCategory(
		@RequestBody InspectionQuestionCategoryCdo inspectionQuestionCategoryCdo) {
		return new ResponseEntity<>(
			inspectionQuestionCategoryLogic.registerInspectionQuestionCategory(inspectionQuestionCategoryCdo),
			HttpStatus.OK);
	}

	@Override
	@PutMapping("/{categoryId}")
	public ResponseEntity<InspectionQuestionCategoryRdo> renameInspectionQuestionCategory(
		@PathVariable("categoryId") String categoryId,
		@RequestBody InspectionQuestionCategoryUdo inspectionQuestionCategoryUdo) {
		return new ResponseEntity<>(
			inspectionQuestionCategoryLogic.renameInspectionQuestionCategory(categoryId,
				inspectionQuestionCategoryUdo),
			HttpStatus.OK);
	}

	@Override
	@PatchMapping("/{categoryId}/status")
	public ResponseEntity<InspectionQuestionCategoryRdo> changeInspectionQuestionCategoryStatus(
		@PathVariable("categoryId") String categoryId,
		@RequestBody InspectionQuestionCategoryStatusUdo inspectionQuestionCategoryStatusUdo) {
		return new ResponseEntity<>(
			inspectionQuestionCategoryLogic.changeInspectionQuestionCategoryStatus(categoryId,
				inspectionQuestionCategoryStatusUdo),
			HttpStatus.OK);
	}

	// 리터럴 "/order"는 Spring이 "/{categoryId}"보다 우선 매칭한다. 확인은
	// InspectionQuestionCategoryResourceTest#modifyInspectionQuestionCategoryOrder_shouldNotBeShadowedByCategoryIdMapping 참고.
	@Override
	@PutMapping("/order")
	public ResponseEntity<Void> modifyInspectionQuestionCategoryOrder(
		@RequestBody List<InspectionQuestionCategoryOrderUdo> orders) {
		inspectionQuestionCategoryLogic.modifyInspectionQuestionCategoryOrder(orders);
		return ResponseEntity.noContent().build();
	}
}
