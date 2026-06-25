package com.seoulchonnom.rest.trip;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.trip.logic.TripLogic;
import com.seoulchonnom.spec.trip.facade.TripFacade;
import com.seoulchonnom.spec.trip.facade.sdo.QuizRdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizResultRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripDetailRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripListRdo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
public class TripResource implements TripFacade {
	private final TripLogic tripLogic;

	@Override
	@GetMapping
	public ResponseEntity<List<TripListRdo>> getAllTrips() {
		return new ResponseEntity<>(tripLogic.getAllTripList(), HttpStatus.OK);
	}

	@Override
	@GetMapping("/{tripId}")
	public ResponseEntity<TripDetailRdo> getTripById(@PathVariable("tripId") String tripId) {
		return new ResponseEntity<>(tripLogic.getTripById(tripId), HttpStatus.OK);
	}

	@Override
	@GetMapping("/quiz/{tripId}")
	public ResponseEntity<QuizRdo> getTripQuiz(@PathVariable("tripId") String tripId) {
		return new ResponseEntity<>(tripLogic.getTripQuiz(tripId), HttpStatus.OK);
	}

	@Override
	@GetMapping("/quiz/check")
	public ResponseEntity<QuizResultRdo> checkTripQuizAnswer(@RequestParam("tripId") String tripId, @RequestParam("optionId") String optionId) {
		return new ResponseEntity<>(tripLogic.checkTripQuizAnswer(tripId, optionId), HttpStatus.OK);
	}

	@Override
	@PostMapping
	public ResponseEntity<TripDetailRdo> createTrip(@RequestBody @Valid TripCdo tripCdo) {
		return new ResponseEntity<>(tripLogic.registerTrip(tripCdo), HttpStatus.OK);
	}
}
