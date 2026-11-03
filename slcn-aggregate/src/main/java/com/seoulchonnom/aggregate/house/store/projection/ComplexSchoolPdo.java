package com.seoulchonnom.aggregate.house.store.projection;

/**
 * 단지의 학교급별 최근접 학교(학교 이름까지 합친 것).
 */
public record ComplexSchoolPdo(String complexId, String level, String schoolName, int distanceM) {
}
