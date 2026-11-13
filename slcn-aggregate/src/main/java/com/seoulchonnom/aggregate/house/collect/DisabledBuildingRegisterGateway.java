package com.seoulchonnom.aggregate.house.collect;

import com.seoulchonnom.aggregate.house.exception.HouseDataSourceUnavailableException;

/**
 * 서비스 키가 없을 때 쓰는 대역. 야간 Flow는 available()로 먼저 걸러 호출하지 않는다.
 */
public class DisabledBuildingRegisterGateway implements BuildingRegisterGateway {
	@Override
	public boolean available() {
		return false;
	}

	@Override
	public RegisterLookupResult find(String lawdCd, String umdCd, String bonbun, String bubun, CallBudget budget,
		RecapFailureTracker tracker) {
		throw new HouseDataSourceUnavailableException("건축물대장");
	}
}
