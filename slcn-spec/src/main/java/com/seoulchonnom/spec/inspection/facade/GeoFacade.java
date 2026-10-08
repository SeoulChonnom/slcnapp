package com.seoulchonnom.spec.inspection.facade;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.inspection.facade.sdo.AddressSearchRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.CurrentLocationSdo;
import com.seoulchonnom.spec.inspection.facade.sdo.GeoPointRdo;

public interface GeoFacade {
	/**
	 * 행안부 도로명주소 검색 프록시. 후보마다 같은 위치 판정용 bdMgtSn과 좌표 조회용 coordKey가 들어 있다.
	 * keyword는 공백 제외 1자 이상, page는 1 이상, size는 1~20이다.
	 */
	ResponseEntity<AddressSearchRdo> searchAddresses(String keyword, int page, int size);

	/**
	 * 현재 위치로 입력한 주소(주소 검색 후보의 coordKey)를 WGS84 좌표로 바꿔 돌려준다.
	 * 저장하지 않고 기록도 남기지 않는다. coordKey가 없거나 필드가 비어 있으면 400(VALIDATION_FAILED),
	 * 행안부 설정이 없으면 503(ADDRESS_LOOKUP_UNAVAILABLE), 조회 실패는 502(ADDRESS_LOOKUP_FAILED)다.
	 */
	ResponseEntity<GeoPointRdo> getCurrentLocation(CurrentLocationSdo currentLocationSdo);
}
