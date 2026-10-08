package com.seoulchonnom.aggregate.inspection.geo;

/**
 * 주소 검색 후보 한 건. bdMgtSn은 같은 위치인지 판단하는 키, coordKey는 좌표 조회용이다.
 */
public record AddressCandidate(String roadAddress, String jibunAddress, String buildingName, String bdMgtSn,
	String zipNo, CoordKey coordKey) {
}
