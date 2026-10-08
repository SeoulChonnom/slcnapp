package com.seoulchonnom.aggregate.inspection.geo;

import com.seoulchonnom.aggregate.inspection.exception.AddressLookupUnavailableException;

/**
 * 승인키가 모두 없는 환경(개발·테스트)의 대역. 호출하면 항상 503이다.
 */
public class DisabledAddressGateway implements AddressGateway {
	@Override
	public AddressSearchResult search(String keyword, int page, int size) {
		throw AddressLookupUnavailableException.notConfigured();
	}

	@Override
	public UtmkPoint findEntrance(CoordKey key) {
		throw AddressLookupUnavailableException.notConfigured();
	}
}
