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

import com.seoulchonnom.aggregate.flow.inspection.InspectionQuestionQueryFlow;
import com.seoulchonnom.aggregate.inspection.logic.InspectionQuestionLogic;
import com.seoulchonnom.spec.inspection.facade.InspectionQuestionFacade;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionContentUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionPolicyUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionStatusUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionVersionRdo;

import lombok.RequiredArgsConstructor;

/**
 * 쓰기는 ADMIN, 조회는 USER다. 권한 분리는 SecurityConfiguration의 메서드별 matcher가 맡는다.
 * 조회까지 막으면 일반 사용자가 매물 생성 시 활성 질문 목록을 읽지 못해 문답을 작성할 수 없다.
 */
@RestController
@RequestMapping("/inspection-questions")
@RequiredArgsConstructor
public class InspectionQuestionResource implements InspectionQuestionFacade {
	private final InspectionQuestionQueryFlow inspectionQuestionQueryFlow;
	private final InspectionQuestionLogic inspectionQuestionLogic;

	@Override
	@GetMapping
	public ResponseEntity<List<InspectionQuestionRdo>> getInspectionQuestions(
		@RequestParam(value = "includeDisabled", defaultValue = "false") boolean includeDisabled,
		@RequestParam(value = "withAnswerCount", defaultValue = "false") boolean withAnswerCount) {
		return new ResponseEntity<>(
			inspectionQuestionQueryFlow.getInspectionQuestions(includeDisabled, withAnswerCount), HttpStatus.OK);
	}

	@Override
	@GetMapping("/{questionId}/versions")
	public ResponseEntity<List<InspectionQuestionVersionRdo>> getInspectionQuestionVersions(
		@PathVariable("questionId") String questionId) {
		return new ResponseEntity<>(inspectionQuestionQueryFlow.getInspectionQuestionVersions(questionId),
			HttpStatus.OK);
	}

	@Override
	@PostMapping
	public ResponseEntity<InspectionQuestionRdo> registerInspectionQuestion(
		@RequestBody InspectionQuestionCdo inspectionQuestionCdo) {
		return new ResponseEntity<>(inspectionQuestionLogic.registerInspectionQuestion(inspectionQuestionCdo),
			HttpStatus.OK);
	}

	@Override
	@PutMapping("/{questionId}")
	public ResponseEntity<InspectionQuestionRdo> modifyInspectionQuestionContent(
		@PathVariable("questionId") String questionId,
		@RequestBody InspectionQuestionContentUdo inspectionQuestionContentUdo) {
		return new ResponseEntity<>(
			inspectionQuestionLogic.modifyInspectionQuestionContent(questionId, inspectionQuestionContentUdo),
			HttpStatus.OK);
	}

	@Override
	@PatchMapping("/{questionId}/policy")
	public ResponseEntity<InspectionQuestionRdo> modifyInspectionQuestionPolicy(
		@PathVariable("questionId") String questionId,
		@RequestBody InspectionQuestionPolicyUdo inspectionQuestionPolicyUdo) {
		return new ResponseEntity<>(
			inspectionQuestionLogic.modifyInspectionQuestionPolicy(questionId, inspectionQuestionPolicyUdo),
			HttpStatus.OK);
	}

	@Override
	@PatchMapping("/{questionId}/status")
	public ResponseEntity<InspectionQuestionRdo> changeInspectionQuestionStatus(
		@PathVariable("questionId") String questionId,
		@RequestBody InspectionQuestionStatusUdo inspectionQuestionStatusUdo) {
		return new ResponseEntity<>(
			inspectionQuestionLogic.changeInspectionQuestionStatus(questionId, inspectionQuestionStatusUdo),
			HttpStatus.OK);
	}

	@Override
	@PutMapping("/order")
	public ResponseEntity<Void> modifyInspectionQuestionOrder(
		@RequestBody List<InspectionQuestionOrderUdo> orders) {
		inspectionQuestionLogic.modifyInspectionQuestionOrder(orders);
		return ResponseEntity.noContent().build();
	}
}
