package com.seoulchonnom.spec.inspection.entity.vo;

/**
 * 지역 목록 정렬 축. 세 축 모두 inspection_area 행에는 없는 집계값이라 DB에서
 * LEFT JOIN + GROUP BY로 계산해 정렬·페이징한다(InspectionAreaRepository 참고).
 */
public enum InspectionAreaSort {
	/**
	 * MAX(visited_at) 내림차순. 임장이 0건인 지역은 맨 뒤, 2차 키는 지역명 오름차순.
	 */
	RECENT_VISIT,
	/**
	 * COUNT(visit) 내림차순. 2차 키는 지역명 오름차순.
	 */
	VISIT_COUNT,
	/**
	 * MAX(interest_level) 내림차순. viewed_property까지 조인해야 한다. NULL은 맨 뒤,
	 * 2차 키는 지역명 오름차순.
	 */
	TOP_INTEREST
}
