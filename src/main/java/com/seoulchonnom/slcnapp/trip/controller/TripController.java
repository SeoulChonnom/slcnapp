package com.seoulchonnom.slcnapp.trip.controller;

import static com.seoulchonnom.slcnapp.trip.TripConstant.*;

import java.nio.file.Paths;

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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/trip")
@RequiredArgsConstructor
@Tag(name = "나들이 정보 API", description = "나들이 상세정보 및 전체 정보 조회")
public class TripController {
	private final TripService tripService;

	@Parameters({
		@Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)})
	@GetMapping("/")
	@Operation(summary = "전체 나들이 조회", description = "메인페이지 리스트 생성용 API")
	public ResponseEntity<BaseResponse> getTrips() {
		return new ResponseEntity<>(
			BaseResponse.from(true, RETRIEVE_TRIP_LIST_SUCCESS_MESSAGE, tripService.getAllTripList()), HttpStatus.OK);
	}

	@Parameters({
		@Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)})
	@GetMapping("/{tripDate}")
	@Operation(summary = "나들이 상세정보 조회", description = "나들이 상세페이지용 API")
	public ResponseEntity<BaseResponse> getTripByDate(@PathVariable("tripDate") String tripDate) {
		return new ResponseEntity<>(
			BaseResponse.from(true, RETRIEVE_TRIP_INFO_SUCCESS_MESSAGE, tripService.getTripByDate(tripDate)),
			HttpStatus.OK);
	}

	@Parameters({
		@Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)})
	@PostMapping(value = "/", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "나들이 추가", description = "나들이 추가용 API")
	public ResponseEntity<BaseResponse> createTrip(
		@RequestPart(value = "tripRegisterRequest") TripRegisterRequest tripRegisterRequest,
		@RequestPart(value = "logo") MultipartFile logo, @RequestPart(value = "map1") MultipartFile map1,
		@RequestPart(required = false, value = "map2") MultipartFile map2) {
		return new ResponseEntity<>(BaseResponse.from(true, REGISTER_TRIP_SUCCESS_MESSAGE,
			tripService.registerTrip(tripRegisterRequest, logo, map1, map2)), HttpStatus.OK);
	}

	@Parameters({
		@Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)})
	@GetMapping("/file")
	@Operation(summary = "이미지 조회", description = "이미지 조회용 API")
	public ResponseEntity<byte[]> getFile(@RequestParam(value = "path") String path) {
		ImageFile imageFile = tripService.getImageFile(Paths.get(path));

		return ResponseEntity.status(HttpStatus.OK)
			.contentType(MediaType.valueOf(imageFile.getMimeType()))
			.body(imageFile.getImage());
	}
}
