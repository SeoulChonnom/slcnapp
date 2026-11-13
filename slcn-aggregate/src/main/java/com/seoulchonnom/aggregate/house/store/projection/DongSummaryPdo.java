package com.seoulchonnom.aggregate.house.store.projection;

/**
 * 동별 후보 단지 수와 중심. 중심은 필터와 무관하게 좌표 있는 전체 단지의 평균이다.
 */
public record DongSummaryPdo(String lawdCd, String umdCd, String umdName, double centerLat, double centerLng,
							 long count) {
}
