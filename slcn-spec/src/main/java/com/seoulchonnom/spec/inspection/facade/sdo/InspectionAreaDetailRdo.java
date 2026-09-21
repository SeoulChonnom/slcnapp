package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회차 탭을 즉시 전환할 수 있도록 회차 요약 목록과 선택 회차 상세를 한 번에 준다.
 *
 * selectedVisit은 GET /inspection-visits/{visitId} 응답과 같은 타입이다.
 * 형태가 갈라지면 회차 지연 로딩 시 FE가 두 모양을 다뤄야 한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InspectionAreaDetailRdo {
	private InspectionAreaRdo area;
	/**
	 * visitedAt 내림차순. 50건을 넘으면 최신 50건으로 자르고 hasMoreVisits를 true로 둔다.
	 */
	private List<InspectionVisitSummaryRdo> visits = new ArrayList<>();
	/**
	 * true면 이 지역에 51번째 이후 회차가 더 있다는 뜻이다. 이어받는 방법:
	 * {@code GET /inspection-visits?areaId={areaId}&page=1&size=50} — 여기 자른 개수(50)와
	 * 임장 목록 페이징의 size 기본값(20)이 다르므로, 51번째 회차부터 정확히 이어받으려면
	 * size를 반드시 50으로 맞춰 요청해야 한다. size를 생략하면(기본 20) 51~70번째를 받게 되어
	 * 지역 상세가 자른 지점과 어긋난다.
	 */
	private boolean hasMoreVisits;
	/**
	 * visitId 파라미터가 없으면 최신 회차. includeProperties=false면 null.
	 */
	private InspectionVisitDetailRdo selectedVisit;
}
