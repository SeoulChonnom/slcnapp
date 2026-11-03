package com.seoulchonnom.aggregate.house.collect;

/**
 * 정비사업 한 건. 데이터셋이 정해지면 구현체가 채운다.
 */
public record RedevelopmentRecord(String sourceKey, String name, String projectType, String stage, Double latitude,
								  Double longitude, String sourceUrl) {
}
