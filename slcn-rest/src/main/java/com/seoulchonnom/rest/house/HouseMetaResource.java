package com.seoulchonnom.rest.house;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.flow.house.HouseQueryFlow;
import com.seoulchonnom.spec.house.facade.HouseMetaFacade;
import com.seoulchonnom.spec.house.facade.sdo.HouseMetaRdo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/house/meta")
@RequiredArgsConstructor
public class HouseMetaResource implements HouseMetaFacade {
	private final HouseQueryFlow houseQueryFlow;

	@Override
	@GetMapping
	public ResponseEntity<HouseMetaRdo> getMeta() {
		return new ResponseEntity<>(houseQueryFlow.getMeta(), HttpStatus.OK);
	}
}
