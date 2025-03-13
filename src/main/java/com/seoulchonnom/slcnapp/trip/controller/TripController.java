package com.seoulchonnom.slcnapp.trip.controller;

import static com.seoulchonnom.slcnapp.trip.TripConstant.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.seoulchonnom.slcnapp.common.dto.BaseResponse;
import com.seoulchonnom.slcnapp.trip.dto.ImageFile;
import com.seoulchonnom.slcnapp.trip.dto.TripRegisterRequest;
import com.seoulchonnom.slcnapp.trip.service.TripService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/trip")
@RequiredArgsConstructor
public class TripController implements TripControllerDocs {
	private final TripService tripService;

	@GetMapping("/")
	public ResponseEntity<BaseResponse> getTrips() {
		return new ResponseEntity<>(
			BaseResponse.from(true, RETRIEVE_TRIP_LIST_SUCCESS_MESSAGE, tripService.getAllTripList()), HttpStatus.OK);
	}

	@GetMapping("/{tripDate}")
	public ResponseEntity<BaseResponse> getTripByDate(@PathVariable("tripDate") String tripDate) {
		return new ResponseEntity<>(
			BaseResponse.from(true, RETRIEVE_TRIP_INFO_SUCCESS_MESSAGE, tripService.getTripByDate(tripDate)),
			HttpStatus.OK);
	}

	@PostMapping(value = "/", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<BaseResponse> createTrip(
		@RequestPart(value = "tripRegisterRequest") TripRegisterRequest tripRegisterRequest,
		@RequestPart(value = "logo") MultipartFile logo, @RequestPart(value = "map1") MultipartFile map1,
		@RequestPart(required = false, value = "map2") MultipartFile map2) {
		return new ResponseEntity<>(BaseResponse.from(true, REGISTER_TRIP_SUCCESS_MESSAGE,
			tripService.registerTrip(tripRegisterRequest, logo, map1, map2)), HttpStatus.OK);
	}

	@GetMapping("/file")
	public ResponseEntity<byte[]> getFile(@RequestParam(value = "path") String path) {
		ImageFile imageFile = tripService.getImageFile(path);

		return ResponseEntity.status(HttpStatus.OK)
			.contentType(MediaType.valueOf(imageFile.getMimeType()))
			.body(imageFile.getImage());
	}
}
