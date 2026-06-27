package com.seoulchonnom.rest.travel;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.travel.logic.TravelLogic;
import com.seoulchonnom.spec.travel.facade.TravelFacade;
import com.seoulchonnom.spec.travel.facade.sdo.TravelCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDetailRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelUdo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/travels")
@RequiredArgsConstructor
public class TravelResource implements TravelFacade {
	private final TravelLogic travelLogic;

	@Override
	@GetMapping
	public ResponseEntity<List<TravelRdo>> getTravels() {
		return new ResponseEntity<>(travelLogic.getTravels(), HttpStatus.OK);
	}

	@Override
	@GetMapping("/{travelId}")
	public ResponseEntity<TravelDetailRdo> getTravel(@PathVariable("travelId") String travelId) {
		return new ResponseEntity<>(travelLogic.getTravel(travelId), HttpStatus.OK);
	}

	@Override
	@PostMapping
	public ResponseEntity<TravelDetailRdo> registerTravel(@RequestBody TravelCdo travelCdo) {
		return new ResponseEntity<>(travelLogic.registerTravel(travelCdo), HttpStatus.OK);
	}

	@Override
	@RequestMapping(value = "/{travelId}", method = {RequestMethod.PUT, RequestMethod.PATCH})
	public ResponseEntity<TravelDetailRdo> modifyTravel(@PathVariable("travelId") String travelId,
		@RequestBody TravelUdo travelUdo) {
		return new ResponseEntity<>(travelLogic.modifyTravel(travelId, travelUdo), HttpStatus.OK);
	}

	@Override
	@DeleteMapping("/{travelId}")
	public ResponseEntity<Void> deleteTravel(@PathVariable("travelId") String travelId) {
		travelLogic.deleteTravel(travelId);
		return ResponseEntity.noContent().build();
	}
}
