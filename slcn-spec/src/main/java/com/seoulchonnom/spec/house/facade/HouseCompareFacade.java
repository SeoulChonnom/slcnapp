package com.seoulchonnom.spec.house.facade;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.house.facade.sdo.CompareRdo;

public interface HouseCompareFacade {
	/**
	 * 비교표. items는 {complexId}:B59,{complexId}:B84 형식으로 1~3개. 없는 단지는 404가 아니라 missingIds로 알린다.
	 */
	ResponseEntity<CompareRdo> getCompare(String items);
}
