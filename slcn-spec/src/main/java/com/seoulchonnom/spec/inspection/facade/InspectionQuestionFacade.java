package com.seoulchonnom.spec.inspection.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCategoryMoveUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionContentUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionPolicyUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionStatusUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionQuestionVersionRdo;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * 쓰기만 ADMIN이고 조회는 USER로 남긴다. 조회까지 막으면 일반 사용자가
 * 매물 생성 시 활성 질문 목록을 읽지 못해 문답을 작성할 수 없다.
 * 물리 삭제 경로는 두지 않는다.
 */
public interface InspectionQuestionFacade {
	ResponseEntity<List<InspectionQuestionRdo>> getInspectionQuestions(boolean includeDisabled,
		boolean withAnswerCount);

	ResponseEntity<List<InspectionQuestionVersionRdo>> getInspectionQuestionVersions(String questionId);

	ResponseEntity<InspectionQuestionRdo> registerInspectionQuestion(InspectionQuestionCdo inspectionQuestionCdo);

	/**
	 * 문구/설명/선택지/단위 수정. 새 버전을 만든다.
	 */
	ResponseEntity<InspectionQuestionRdo> modifyInspectionQuestionContent(String questionId,
		InspectionQuestionContentUdo inspectionQuestionContentUdo);

	/**
	 * required, sortOrder 변경. 버전을 유지한다.
	 */
	ResponseEntity<InspectionQuestionRdo> modifyInspectionQuestionPolicy(String questionId,
		InspectionQuestionPolicyUdo inspectionQuestionPolicyUdo);

	ResponseEntity<InspectionQuestionRdo> changeInspectionQuestionStatus(String questionId,
		InspectionQuestionStatusUdo inspectionQuestionStatusUdo);

	@ApiResponse(responseCode = "204", description = "성공. 본문 없음")
	ResponseEntity<Void> modifyInspectionQuestionOrder(List<InspectionQuestionOrderUdo> orders);

	/**
	 * 질문을 다른 분류로 옮긴다. 대상 분류의 맨 뒤에 배치되고, 버전은 올리지 않는다(계획 §1).
	 */
	ResponseEntity<InspectionQuestionRdo> moveInspectionQuestionCategory(String questionId,
		InspectionQuestionCategoryMoveUdo inspectionQuestionCategoryMoveUdo);
}
