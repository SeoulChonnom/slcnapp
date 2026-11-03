package com.seoulchonnom.aggregate.house.logic;

/**
 * 통계 재생성 결과(배치 기록용 건수).
 *
 * @param candidates 이상치 판정 대상 거래 수
 * @param outliers 이상치로 판정해 표시한 거래 수
 * @param statRows 새로 만든 (단지, 면적 구간) 통계 행 수
 */
public record StatRebuildResult(int candidates, int outliers, int statRows) {
}
