package com.seoulchonnom.spec.inspection.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.common.response.PageRdo;
import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;
import com.seoulchonnom.spec.inspection.facade.sdo.FileBoxItemOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitStatusUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ReviewSuggestionRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ReviewSuggestionSdo;
import com.seoulchonnom.spec.inspection.facade.sdo.WalkingRouteRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyOrderUdo;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

public interface InspectionVisitFacade {
	/**
	 * visitedAt 내림차순, 동점은 id 오름차순. 모든 필터(areaId/status/revisitIntent/tag/from/to)는
	 * 선택이며 null/빈 값이면 적용하지 않는다. tag를 여러 개 지정하면 AND다.
	 * 지역 상세가 회차를 50건으로 자른 뒤(hasMoreVisits) 이어받는 용도로도 areaId+page/size를 쓴다.
	 * page(0-base, 기본 0)/size(기본 20, 상한 100)는 offset 페이징이다.
	 */
	ResponseEntity<PageRdo<InspectionVisitRdo>> getInspectionVisits(String areaId, InspectionStatus status,
		RevisitIntent revisitIntent, List<String> tags, String from, String to, int page, int size);

	ResponseEntity<InspectionVisitDetailRdo> getInspectionVisit(String visitId);

	ResponseEntity<InspectionVisitDetailRdo> registerInspectionVisit(InspectionVisitCdo inspectionVisitCdo);

	ResponseEntity<InspectionVisitDetailRdo> modifyInspectionVisit(String visitId,
		InspectionVisitUdo inspectionVisitUdo);

	ResponseEntity<InspectionVisitDetailRdo> changeInspectionVisitStatus(String visitId,
		InspectionVisitStatusUdo inspectionVisitStatusUdo);

	/**
	 * 요청에 빠진 매물은 기존 순서를 유지한다. 상태 전이를 유발하지 않으므로 COMPLETED 임장에서도 허용한다.
	 */
	@ApiResponse(responseCode = "204", description = "성공. 본문 없음")
	ResponseEntity<Void> modifyViewedPropertyOrder(String visitId, List<ViewedPropertyOrderUdo> orders);

	/**
	 * 임장 사진과 매물 사진을 함께 처리한다. itemId가 FileBox 문서 안에서 유일하다.
	 */
	@ApiResponse(responseCode = "204", description = "성공. 본문 없음")
	ResponseEntity<Void> modifyInspectionImageOrder(String visitId, List<FileBoxItemOrderUdo> orders);

	/**
	 * 하위 매물/문답/태그 연결을 먼저 지우고 RDB 커밋에 성공한 뒤에 FileBox를 지운다.
	 */
	@ApiResponse(responseCode = "204", description = "성공. 본문 없음")
	ResponseEntity<Void> deleteInspectionVisit(String visitId);

	/**
	 * 폼에 입력 중인 메모/장점으로 한줄평·장점·단점·태그를 AI가 제안한다. **제안만 하고 아무것도 저장하지 않는다** —
	 * FE가 폼을 채우고 사용자가 기존 PUT으로 저장한다. memo는 필수(비면 400 VALIDATION_FAILED)이고 pros는 참고용 선택 값이다.
	 * AI 설정이 없거나 호출이 실패하면 503(REVIEW_SUGGESTION_UNAVAILABLE)이다.
	 */
	ResponseEntity<ReviewSuggestionRdo> suggestInspectionVisitReview(String visitId,
		ReviewSuggestionSdo reviewSuggestionSdo);

	/**
	 * 저장된 매물 위치를 화면 순서대로 이어 카카오 도보 경로를 조회한다. 본문이 없고 아무것도 저장하지 않는다.
	 * 위치 없는 매물은 건너뛰고 연속한 같은 좌표는 한 지점으로 합친다. 지점이 2개 미만이면 외부 호출 없이 빈 경로다.
	 * 지점이 7개를 넘으면 앞 구간의 끝점을 다음 구간의 시작점으로 겹쳐 나눠 호출하며, 하나라도 실패하면 전체가 실패한다.
	 * 설정이 없거나 키가 거절되면 503(WALKING_ROUTE_UNAVAILABLE), 한도 초과는 429(WALKING_ROUTE_QUOTA_EXCEEDED),
	 * 그 밖의 실패는 502(WALKING_ROUTE_FAILED)이다.
	 */
	ResponseEntity<WalkingRouteRdo> getWalkingRoute(String visitId);
}
