package com.seoulchonnom.spec.inspection.entity.vo;

/**
 * 단지명 자동완성 후보의 조회 범위.
 *
 * VISIT은 이 임장에서 이미 쓴 이름만 준다(기존 동작, 하위호환 기본값).
 * AREA는 같은 지역의 모든 회차에서 쓴 이름을 준다 — 자동완성의 목적이
 * "같은 이름이어야 회차 간 매물이 연결됩니다"라서, 이 임장이 처음이라 이름이 하나도
 * 없을 때 과거 회차의 이름이야말로 후보가 되기 때문이다.
 */
public enum ComplexNameScope {
	VISIT,
	AREA
}
