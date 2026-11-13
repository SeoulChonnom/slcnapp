package com.seoulchonnom.aggregate.house.store.projection;

/**
 * 지도 영역 목록의 한 행. 통계가 없으면 recent* 가 null이다.
 */
public record ComplexMapPdo(String complexId, String aptName, String lawdCd, String umdName, Double latitude,
							Double longitude, Integer buildYear, Integer households, Long recentPriceManwon,
							Integer recentWindowMonths, Integer recentSample) {
}
