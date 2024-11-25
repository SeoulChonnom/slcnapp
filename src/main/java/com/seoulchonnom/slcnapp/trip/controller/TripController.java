package com.seoulchonnom.slcnapp.trip.controller;

import static com.seoulchonnom.slcnapp.trip.TripConstant.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.slcnapp.common.dto.BaseResponse;
import com.seoulchonnom.slcnapp.trip.service.TripService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/trip")
@RequiredArgsConstructor
@Tag(name = "나들이 정보 API", description = "나들이 상세정보 및 전체 정보 조회")
public class TripController {
	private final TripService tripService;

	@GetMapping("/")
	@Operation(summary = "전체 나들이 조회", description = "메인페이지 리스트 생성용 API")
	public ResponseEntity<BaseResponse> getTrips() {
		return new ResponseEntity<>(
			BaseResponse.from(true, RETRIEVE_TRIP_LIST_SUCCESS_MESSAGE, tripService.getAllTripList()),
			HttpStatus.OK);
	}

	@GetMapping("/{tripId}")
	@Operation(summary = "나들이 상세정보 조회", description = "나들이 상세페이지용 API")
	public ResponseEntity<BaseResponse> getTripById(@PathVariable("tripId") Integer tripId) {
		return new ResponseEntity<>(
			BaseResponse.from(true, RETRIEVE_TRIP_INFO_SUCCESS_MESSAGE, tripService.getTripById(tripId)),
			HttpStatus.OK);
	}
}
