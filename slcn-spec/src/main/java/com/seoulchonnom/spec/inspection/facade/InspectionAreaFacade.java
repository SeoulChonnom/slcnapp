package com.seoulchonnom.spec.inspection.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.inspection.entity.vo.InspectionAreaSort;
import com.seoulchonnom.spec.inspection.entity.vo.RevisitIntent;
import com.seoulchonnom.spec.inspection.facade.sdo.AreaViewedPropertyRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaCdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaDetailRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaListRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.InspectionAreaUdo;

public interface InspectionAreaFacade {
	/**
	 * keyword는 지역명·설명·단지명·매물명·태그명을 모두 대소문자 무시로 훑는다.
	 * revisitIntent는 지역의 최신 회차 기준이다. sort 기본값은 RECENT_VISIT이다.
	 * page(0-base, 기본 0)/size(기본 20, 상한 100)는 offset 페이징이며, size 상한 초과는
	 * 400 대신 상한으로 깎고 page 음수만 400이다.
	 */
	ResponseEntity<InspectionAreaListRdo> getInspectionAreas(String keyword, RevisitIntent revisitIntent,
		InspectionAreaSort sort, int page, int size);

	/**
	 * 지역 전체 매물 경량 목록. 회차 하나만 담는 지역 상세와 달리 모든 회차의 매물을 평면으로 준다.
	 * complexName+name으로 회차 간 매물을 잇는 FE 기능의 재료다. answers는 읽지 않는다.
	 * 정렬은 visitedAt 내림차순, 같은 회차 안에서는 sortOrder 오름차순이다.
	 */
	ResponseEntity<List<AreaViewedPropertyRdo>> getAreaProperties(String areaId);

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
