package com.seoulchonnom.aggregate.house.store.projection;

/**
 * 좌표가 확정된 단지의 위치. 최근접 학교 계산 입력이다.
 */
public record ComplexPointPdo(String complexId, double latitude, double longitude) {
}
