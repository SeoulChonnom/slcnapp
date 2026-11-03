package com.seoulchonnom.aggregate.house.collect;

import java.util.List;

import com.seoulchonnom.aggregate.house.exception.HouseDataSourceUnavailableException;

public class DisabledRedevelopmentGateway implements RedevelopmentGateway {
	@Override
	public boolean available() {
		return false;
	}

	@Override
	public List<RedevelopmentRecord> fetchAll() {
		throw new HouseDataSourceUnavailableException("정비사업");
	}
}
