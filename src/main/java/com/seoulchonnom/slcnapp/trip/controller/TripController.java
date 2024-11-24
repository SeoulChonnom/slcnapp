package com.seoulchonnom.slcnapp.trip.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.slcnapp.common.dto.BaseResponse;
import com.seoulchonnom.slcnapp.trip.service.TripService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/trip")
@RequiredArgsConstructor
public class TripController {
	private final TripService tripService;

	@GetMapping("/")
	public ResponseEntity<BaseResponse> getTrips() {
		return new ResponseEntity<>(BaseResponse.from(true, "", tripService.getAllTripList()), HttpStatus.OK);
	}
}
