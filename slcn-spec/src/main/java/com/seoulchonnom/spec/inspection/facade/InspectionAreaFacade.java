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
	 *
	 * complexName/name은 각각 독립적인 정확 일치 필터다. 회차 연결 스트립은 한 매물의 이력만
	 * 쓰므로 둘 다 지정해 호출하면 응답이 그 매물의 회차 수만큼으로 줄어든다.
	 * 저장 시 trim + 연속 공백 1칸으로 정규화되므로 같은 규칙으로 정규화한 값을 보내면 된다.
	 *
	 * 페이지 래퍼를 씌우지 않는다. 이 목록의 소비처는 "이 매물이 회차를 거치며 어떻게 변했나"를
	 * 한 번에 그리는 것이라 일부만 받으면 쓸모가 없다. 지역 하나에 묶인 매물이 모수라 상한도 분명하다.
	 */
	ResponseEntity<List<AreaViewedPropertyRdo>> getAreaProperties(String areaId, String complexName, String name);

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
