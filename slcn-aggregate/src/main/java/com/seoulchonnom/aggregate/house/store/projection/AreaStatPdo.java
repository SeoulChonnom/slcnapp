package com.seoulchonnom.aggregate.house.store.projection;

/**
 * (단지, 면적 구간) 통계 중 최근 실거래가. 6개월에도 거래가 없으면 priceManwon이 null이다.
 */
public record AreaStatPdo(Long recentPriceManwon, Integer recentWindowMonths, int recentSample) {
}
