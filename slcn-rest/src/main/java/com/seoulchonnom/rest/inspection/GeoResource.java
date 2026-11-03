package com.seoulchonnom.rest.inspection;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.flow.inspection.GeoQueryFlow;
import com.seoulchonnom.spec.inspection.facade.GeoFacade;
import com.seoulchonnom.spec.inspection.facade.sdo.AddressSearchRdo;
import com.seoulchonnom.spec.inspection.facade.sdo.CurrentLocationSdo;
import com.seoulchonnom.spec.inspection.facade.sdo.GeoPointRdo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/geo")
@RequiredArgsConstructor
public class GeoResource implements GeoFacade {
	private final GeoQueryFlow geoQueryFlow;

	@Override
	@GetMapping("/addresses")
	public ResponseEntity<AddressSearchRdo> searchAddresses(@RequestParam("keyword") String keyword,
		@RequestParam(value = "page", defaultValue = "1") int page,
		@RequestParam(value = "size", defaultValue = "10") int size) {
		return ResponseEntity.ok(geoQueryFlow.searchAddresses(keyword, page, size));
	}

	@Override
	@PostMapping("/current-location")
	public ResponseEntity<GeoPointRdo> getCurrentLocation(@RequestBody CurrentLocationSdo currentLocationSdo) {
		return ResponseEntity.ok(geoQueryFlow.getCurrentLocation(currentLocationSdo));
	}
}
