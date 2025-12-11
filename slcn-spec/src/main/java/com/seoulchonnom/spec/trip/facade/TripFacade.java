package com.seoulchonnom.spec.trip.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.trip.facade.sdo.TripCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripInfoRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripListRdo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "나들이 정보 API", description = "나들이 상세정보 및 전체 정보 조회")
public interface TripFacade {

	@Parameters({
		@Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)})
	@Operation(summary = "전체 나들이 조회", description = "메인페이지 리스트 생성용 API")
	ResponseEntity<List<TripListRdo>> getAllTrips();

	@Parameters({
		@Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)})
	@Operation(summary = "나들이 상세정보 조회", description = "나들이 상세페이지용 API")
	ResponseEntity<TripInfoRdo> getTripByDate(String tripDate);

	@Parameters({
		@Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)})
	@Operation(summary = "나들이 추가", description = "나들이 추가용 API")
	ResponseEntity<TripInfoRdo> createTrip(TripCdo tripCdo);
}
