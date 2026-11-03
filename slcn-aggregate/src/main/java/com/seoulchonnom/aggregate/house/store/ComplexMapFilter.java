package com.seoulchonnom.aggregate.house.store;

/**
 * 지도 목록 쿼리의 조건. PostgreSQL이 null 파라미터의 타입을 추론하지 못해서, 조건이 없는 항목은
 * 플래그(false)와 기본값으로 넘긴다. 파라미터에 null을 싣지 않는다.
 */
public record ComplexMapFilter(boolean bounded, double swLat, double swLng, double neLat, double neLng,
							   boolean byDistrict, String lawdCd, boolean hasPrice, long minPrice, long maxPrice) {
	public static ComplexMapFilter ofBounds(double swLat, double swLng, double neLat, double neLng, Long minPrice,
		Long maxPrice) {
		return new ComplexMapFilter(true, swLat, swLng, neLat, neLng, false, "", minPrice != null || maxPrice != null,
			minPrice == null ? 0 : minPrice, maxPrice == null ? Long.MAX_VALUE : maxPrice);
	}

	public static ComplexMapFilter ofDistrict(String lawdCd, Long minPrice, Long maxPrice) {
		return new ComplexMapFilter(false, 0, 0, 0, 0, true, lawdCd, minPrice != null || maxPrice != null,
			minPrice == null ? 0 : minPrice, maxPrice == null ? Long.MAX_VALUE : maxPrice);
	}
}
