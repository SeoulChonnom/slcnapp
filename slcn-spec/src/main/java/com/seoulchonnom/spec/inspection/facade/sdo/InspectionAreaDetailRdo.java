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
	 * true면 이 지역에 visitPageSize번째 이후 회차가 더 있다는 뜻이다.
	 * 이어받는 방법은 visitPageSize 주석을 본다.
	 */
	private boolean hasMoreVisits;
	/**
	 * visits를 자른 개수. 이어받을 때 이 값을 그대로 size로 넘긴다:
	 * {@code GET /inspection-visits?areaId={areaId}&page=1&size={visitPageSize}}
	 *
	 * 값을 응답에 싣는 이유는 FE가 상수를 박지 않게 하기 위해서다. 임장 목록 페이징의
	 * size 기본값(20)과 이 값이 다르므로 size를 생략하면 자른 지점과 어긋나는데,
	 * FE가 50을 하드코딩해 두면 여기를 바꾸는 순간 조용히 틀어진다.
	 */
	private int visitPageSize;
	/**
	 * visitId 파라미터가 없으면 최신 회차. includeProperties=false면 null.
	 */
	private InspectionVisitDetailRdo selectedVisit;
}
