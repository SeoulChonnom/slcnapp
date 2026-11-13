package com.seoulchonnom.spec.house.entity.vo;

/**
 * 야간 배치 단계. 순서가 실행 순서이고, 값은 house_batch_run.step에 문자열로 저장된다.
 */
public enum BatchStep {
	COLLECT_UNIT_PREPARE, TRADE_COLLECT, COMPLEX_ENRICH, SCHOOL_SYNC, REDEVELOPMENT_SYNC, STATS_BUILD
}
