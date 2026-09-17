package com.seoulchonnom.spec.inspection.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaUdo;

public interface InspectionAreaFacade {
	ResponseEntity<List<InspectionAreaRdo>> getInspectionAreas(String keyword);

	/**
	 * 회차 요약 목록과 선택 회차 상세를 한 번에 반환한다.
	 *
	 * @param visitId           null이면 최신 회차를 펼친다
	 * @param includeProperties false면 selectedVisit을 생략한다. 회차가 많은 지역의 지연 로딩용
	 */
	ResponseEntity<InspectionAreaDetailRdo> getInspectionArea(String areaId, String visitId,
		boolean includeProperties);

	/**
	 * 동일 지역명이 이미 있으면 409로 막는다. 중복 생성되면 같은 생활권의 재임장 이력이 두 갈래로 갈라진다.
	 */
	ResponseEntity<InspectionAreaRdo> registerInspectionArea(InspectionAreaCdo inspectionAreaCdo);

	ResponseEntity<InspectionAreaRdo> modifyInspectionArea(String areaId, InspectionAreaUdo inspectionAreaUdo);

	/**
	 * 임장 기록이 1건이라도 있으면 409로 막는다.
	 */
	ResponseEntity<Void> deleteInspectionArea(String areaId);
}
