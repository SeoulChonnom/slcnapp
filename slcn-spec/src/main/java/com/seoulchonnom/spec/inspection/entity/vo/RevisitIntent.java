package com.seoulchonnom.spec.inspection.entity.vo;

/**
 * 재방문 의사. 지역 자체의 속성이 아니라 특정 임장 시점의 판단이므로 InspectionVisit이 가진다.
 */
public enum RevisitIntent {
	YES,
	MAYBE,
	NO
}
