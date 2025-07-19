package com.seoulchonnom.slcnapp.trip.controller;

import com.seoulchonnom.slcnapp.common.dto.BaseResponse;
import com.seoulchonnom.slcnapp.trip.dto.TripRegisterRequest;
import com.seoulchonnom.slcnapp.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.seoulchonnom.slcnapp.trip.TripConstant.*;

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

    @PostMapping(value = "/")
    public ResponseEntity<BaseResponse> createTrip(@RequestBody TripRegisterRequest tripRegisterRequest) {
        return new ResponseEntity<>(BaseResponse.from(true, REGISTER_TRIP_SUCCESS_MESSAGE,
                tripService.registerTrip(tripRegisterRequest)), HttpStatus.OK);
    }
}
