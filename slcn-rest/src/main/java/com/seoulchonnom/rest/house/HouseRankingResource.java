package com.seoulchonnom.rest.house;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.flow.house.HouseQueryFlow;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.RankingType;
import com.seoulchonnom.spec.house.facade.HouseRankingFacade;
import com.seoulchonnom.spec.house.facade.sdo.RankingListRdo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/house/rankings")
@RequiredArgsConstructor
public class HouseRankingResource implements HouseRankingFacade {
	private final HouseQueryFlow houseQueryFlow;

	@Override
	@GetMapping
	public ResponseEntity<RankingListRdo> getRankings(
		@RequestParam(value = "type", required = false) RankingType type,
		@RequestParam(value = "area", required = false) AreaBucket area) {
		return new ResponseEntity<>(houseQueryFlow.getRankings(type, area), HttpStatus.OK);
	}
}
