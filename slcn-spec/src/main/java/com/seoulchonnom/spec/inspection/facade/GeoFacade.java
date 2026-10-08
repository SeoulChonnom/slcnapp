package com.seoulchonnom.spec.inspection.facade;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.inspection.facade.sdo.AddressSearchRdo;

public interface GeoFacade {
	/**
	 * 행안부 도로명주소 검색 프록시. 후보마다 같은 위치 판정용 bdMgtSn과 좌표 조회용 coordKey가 들어 있다.
	 * keyword는 공백 제외 1자 이상, page는 1 이상, size는 1~20이다.
	 */
	ResponseEntity<AddressSearchRdo> searchAddresses(String keyword, int page, int size);
}
