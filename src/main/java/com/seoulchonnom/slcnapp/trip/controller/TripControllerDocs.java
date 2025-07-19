package com.seoulchonnom.slcnapp.trip.controller;

import com.seoulchonnom.slcnapp.common.dto.BaseResponse;
import com.seoulchonnom.slcnapp.trip.dto.TripRegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "나들이 정보 API", description = "나들이 상세정보 및 전체 정보 조회")
public interface TripControllerDocs {

    @Parameters({
            @Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)})
    @Operation(summary = "전체 나들이 조회", description = "메인페이지 리스트 생성용 API")
    ResponseEntity<BaseResponse> getTrips();

    @Parameters({
            @Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)})
    @Operation(summary = "나들이 상세정보 조회", description = "나들이 상세페이지용 API")
    ResponseEntity<BaseResponse> getTripByDate(String tripDate);

    @Parameters({
            @Parameter(name = "X-AUTH-TOKEN", description = "AccessToken", required = true, in = ParameterIn.HEADER)})
    @Operation(summary = "나들이 추가", description = "나들이 추가용 API")
    ResponseEntity<BaseResponse> createTrip(TripRegisterRequest tripRegisterRequest);
}
