package com.seoulchonnom.rest.house;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.flow.house.HouseCompareQueryFlow;
import com.seoulchonnom.spec.house.facade.HouseCompareFacade;
import com.seoulchonnom.spec.house.facade.sdo.CompareRdo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/house/compare")
@RequiredArgsConstructor
public class HouseCompareResource implements HouseCompareFacade {
	private final HouseCompareQueryFlow houseCompareQueryFlow;

	/**
	 * items가 없거나 비어도 400 VALIDATION_FAILED + errors[]로 내려야 해서 required = false로 받고 Flow가 검증한다.
	 */
	@Override
	@GetMapping
	public ResponseEntity<CompareRdo> getCompare(@RequestParam(value = "items", required = false) String items) {
		return new ResponseEntity<>(houseCompareQueryFlow.compare(items), HttpStatus.OK);
	}
}
