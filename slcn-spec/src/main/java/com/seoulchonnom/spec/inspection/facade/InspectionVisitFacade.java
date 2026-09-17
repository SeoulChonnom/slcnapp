package com.seoulchonnom.spec.inspection.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.inspection.entity.vo.InspectionStatus;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;
import com.seoulchonnom.spec.inspection.facade.sdo.FileBoxItemOrderUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitStatusUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionVisitUdo;
import com.seoulchonnom.spec.inspection.facade.sdo.ViewedPropertyOrderUdo;

public interface InspectionVisitFacade {
	/**
	 * visitedAt 내림차순. 모든 필터는 선택이며 null이면 적용하지 않는다.
	 */
	ResponseEntity<List<InspectionVisitRdo>> getInspectionVisits(String areaId, InspectionStatus status,
		RevisitIntent revisitIntent, List<String> tags, String from, String to);

	ResponseEntity<InspectionVisitDetailRdo> getInspectionVisit(String visitId);

	ResponseEntity<InspectionVisitDetailRdo> registerInspectionVisit(InspectionVisitCdo inspectionVisitCdo);

	ResponseEntity<InspectionVisitDetailRdo> modifyInspectionVisit(String visitId,
		InspectionVisitUdo inspectionVisitUdo);

	ResponseEntity<InspectionVisitDetailRdo> changeInspectionVisitStatus(String visitId,
		InspectionVisitStatusUdo inspectionVisitStatusUdo);

	/**
	 * 요청에 빠진 매물은 기존 순서를 유지한다. 상태 전이를 유발하지 않으므로 COMPLETED 임장에서도 허용한다.
	 */
	ResponseEntity<Void> modifyViewedPropertyOrder(String visitId, List<ViewedPropertyOrderUdo> orders);

	/**
	 * 임장 사진과 매물 사진을 함께 처리한다. itemId가 FileBox 문서 안에서 유일하다.
	 */
	ResponseEntity<Void> modifyInspectionImageOrder(String visitId, List<FileBoxItemOrderUdo> orders);

	/**
	 * 하위 매물/문답/태그 연결을 먼저 지우고 RDB 커밋에 성공한 뒤에 FileBox를 지운다.
	 */
	ResponseEntity<Void> deleteInspectionVisit(String visitId);
}
