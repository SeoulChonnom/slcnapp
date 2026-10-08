package com.seoulchonnom.aggregate.inspection.geo;

import com.seoulchonnom.aggregate.inspection.exception.AddressLookupUnavailableException;
import com.seoulchonnom.aggregate.inspection.exception.InvalidAddressKeywordException;

/**
 * 주소 검색·좌표 조회 포트. 구현체가 어떤 API를 쓰는지는 도메인이 모른다.
 */
public interface AddressGateway {
	/**
	 * @param page 1부터 시작
	 * @throws InvalidAddressKeywordException 검색어를 상대가 거절했을 때
	 * @throws AddressLookupUnavailableException 설정이 없거나 호출/응답 해석에 실패했을 때
	 */
	AddressSearchResult search(String keyword, int page, int size);

	/**
	 * @throws AddressLookupUnavailableException 설정이 없거나 호출/응답 해석에 실패했을 때
	 */
	UtmkPoint findEntrance(CoordKey key);
}
