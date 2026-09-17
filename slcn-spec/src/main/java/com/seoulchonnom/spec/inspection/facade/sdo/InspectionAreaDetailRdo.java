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
	private boolean hasMoreVisits;
	/**
	 * visitId 파라미터가 없으면 최신 회차. includeProperties=false면 null.
	 */
	private InspectionVisitDetailRdo selectedVisit;
}
