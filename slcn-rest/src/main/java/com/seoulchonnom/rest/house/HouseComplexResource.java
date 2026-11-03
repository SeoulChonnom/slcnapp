package com.seoulchonnom.rest.house;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.flow.house.HouseQueryFlow;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.ComplexGroupBy;
import com.seoulchonnom.spec.house.facade.HouseComplexFacade;
import com.seoulchonnom.spec.house.facade.sdo.ComplexCardRdo;
import com.seoulchonnom.spec.house.facade.sdo.ComplexMapListRdo;
import com.seoulchonnom.spec.house.facade.sdo.ComplexSearchRdo;
import com.seoulchonnom.spec.house.facade.sdo.ComplexSummaryRdo;

import lombok.RequiredArgsConstructor;

/**
 * 리터럴 경로(/summary, /search)가 변수 경로(/{complexId})보다 우선한다. 테스트로 고정한다.
 */
@RestController
@RequestMapping("/house/complexes")
@RequiredArgsConstructor
public class HouseComplexResource implements HouseComplexFacade {
	private final HouseQueryFlow houseQueryFlow;

	@Override
	@GetMapping
	public ResponseEntity<ComplexMapListRdo> getComplexes(
		@RequestParam(value = "swLat", required = false) Double swLat,
		@RequestParam(value = "swLng", required = false) Double swLng,
		@RequestParam(value = "neLat", required = false) Double neLat,
		@RequestParam(value = "neLng", required = false) Double neLng,
		@RequestParam(value = "lawdCd", required = false) String lawdCd,
		@RequestParam(value = "area", required = false) AreaBucket area,
		@RequestParam(value = "minPrice", required = false) Long minPrice,
		@RequestParam(value = "maxPrice", required = false) Long maxPrice) {
		return new ResponseEntity<>(
			houseQueryFlow.getComplexes(swLat, swLng, neLat, neLng, lawdCd, area, minPrice, maxPrice), HttpStatus.OK);
	}

	@Override
	@GetMapping("/summary")
	public ResponseEntity<ComplexSummaryRdo> getSummary(
		@RequestParam(value = "groupBy", required = false) ComplexGroupBy groupBy,
		@RequestParam(value = "area", required = false) AreaBucket area,
		@RequestParam(value = "minPrice", required = false) Long minPrice,
		@RequestParam(value = "maxPrice", required = false) Long maxPrice,
		@RequestParam(value = "swLat", required = false) Double swLat,
		@RequestParam(value = "swLng", required = false) Double swLng,
		@RequestParam(value = "neLat", required = false) Double neLat,
		@RequestParam(value = "neLng", required = false) Double neLng) {
		return new ResponseEntity<>(
			houseQueryFlow.getSummary(groupBy, area, minPrice, maxPrice, swLat, swLng, neLat, neLng), HttpStatus.OK);
	}

	@Override
	@GetMapping("/search")
	public ResponseEntity<ComplexSearchRdo> searchComplexes(
		@RequestParam(value = "keyword", required = false) String keyword,
		@RequestParam(value = "size", required = false) Integer size) {
		return new ResponseEntity<>(houseQueryFlow.searchComplexes(keyword, size), HttpStatus.OK);
	}

	@Override
	@GetMapping("/{complexId}")
	public ResponseEntity<ComplexCardRdo> getComplex(@PathVariable("complexId") String complexId,
		@RequestParam(value = "area", required = false) AreaBucket area) {
		return new ResponseEntity<>(houseQueryFlow.getComplex(complexId, area), HttpStatus.OK);
	}
}
