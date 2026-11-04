package com.seoulchonnom.aggregate.house.store.projection;

/**
 * 단지 반경 안 정비사업(사업 정보까지 합친 것).
 */
public record ComplexRedevelopmentPdo(String complexId, String name, String projectType, String stage, int distanceM,
									  String sourceUrl) {
}
