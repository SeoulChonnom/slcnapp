package com.seoulchonnom.aggregate.house.store.projection;

/**
 * 구별 후보 단지 수. 좌표가 있는 단지가 없는 구는 행이 없다.
 */
public record DistrictCountPdo(String lawdCd, long count) {
}
