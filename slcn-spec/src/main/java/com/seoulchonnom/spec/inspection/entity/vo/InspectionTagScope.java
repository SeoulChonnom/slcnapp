package com.seoulchonnom.spec.inspection.entity.vo;

/**
 * 태그 자동완성이 usageCount를 어느 연결 테이블 기준으로 셀지 정한다.
 * 태그 마스터 풀은 임장/매물이 공유하므로(B-⑨), scope는 이름 검색(findAllByKeyword)이 아니라
 * 사용 빈도 집계에만 영향을 준다. null이면 기존처럼 두 연결을 합산한다.
 */
public enum InspectionTagScope {
	VISIT,
	PROPERTY
}
