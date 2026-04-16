package com.seoulchonnom.rest.trip;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.trip.logic.TripLogic;
import com.seoulchonnom.spec.trip.facade.TripFacade;
import com.seoulchonnom.spec.trip.facade.sdo.TripCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripInfoRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripListRdo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/trip")
@RequiredArgsConstructor
public class TripResource implements TripFacade {
	private final TripLogic tripLogic;

	@Override
	@GetMapping
	public ResponseEntity<List<TripListRdo>> getAllTrips() {
		return new ResponseEntity<>(tripLogic.getAllTripList(), HttpStatus.OK);
	}

	@Override
	@GetMapping("/detail")
	public ResponseEntity<TripInfoRdo> getTripByDate(@RequestParam("tripDate") String tripDate) {
		return new ResponseEntity<>(tripLogic.getTripInfoByDate(tripDate), HttpStatus.OK);
	}

	@Override
	@PostMapping
	public ResponseEntity<TripInfoRdo> createTrip(@RequestBody @Valid TripCdo tripCdo) {
		return new ResponseEntity<>(tripLogic.registerTrip(tripCdo), HttpStatus.OK);
	}
}
