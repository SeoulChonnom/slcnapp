package com.seoulchonnom.rest.travel;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.seoulchonnom.aggregate.travel.logic.TravelLogic;
import com.seoulchonnom.spec.travel.facade.TravelFacade;
import com.seoulchonnom.spec.travel.facade.sdo.TravelCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDayRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDayUdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDetailRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelPhotoCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelPhotoRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelPlaceCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelPlaceRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelPlaceUdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelReviewRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelReviewUdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelTagCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelTagRdo;
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

	@Override
	@PatchMapping("/{travelId}/days/{travelDayId}")
	public ResponseEntity<TravelDayRdo> modifyDay(@PathVariable("travelId") String travelId,
		@PathVariable("travelDayId") String travelDayId, @RequestBody TravelDayUdo travelDayUdo) {
		return new ResponseEntity<>(travelLogic.modifyDay(travelId, travelDayId, travelDayUdo), HttpStatus.OK);
	}

	@Override
	@PostMapping("/{travelId}/days/{travelDayId}/places")
	public ResponseEntity<TravelPlaceRdo> registerPlace(@PathVariable("travelId") String travelId,
		@PathVariable("travelDayId") String travelDayId,
		@RequestBody TravelPlaceCdo travelPlaceCdo) {
		return new ResponseEntity<>(travelLogic.registerPlace(travelId, travelDayId, travelPlaceCdo), HttpStatus.OK);
	}

	@Override
	@PatchMapping("/{travelId}/days/{travelDayId}/places/{placeId}")
	public ResponseEntity<TravelPlaceRdo> modifyPlace(@PathVariable("travelId") String travelId,
		@PathVariable("travelDayId") String travelDayId, @PathVariable("placeId") String placeId,
		@RequestBody TravelPlaceUdo travelPlaceUdo) {
		return new ResponseEntity<>(travelLogic.modifyPlace(travelId, travelDayId, placeId, travelPlaceUdo),
			HttpStatus.OK);
	}

	@Override
	@DeleteMapping("/{travelId}/days/{travelDayId}/places/{placeId}")
	public ResponseEntity<Void> deletePlace(@PathVariable("travelId") String travelId,
		@PathVariable("travelDayId") String travelDayId, @PathVariable("placeId") String placeId) {
		travelLogic.deletePlace(travelId, travelDayId, placeId);
		return ResponseEntity.noContent().build();
	}

	@Override
	@PostMapping("/{travelId}/photos")
	public ResponseEntity<TravelPhotoRdo> registerPhoto(@PathVariable("travelId") String travelId,
		@RequestBody TravelPhotoCdo travelPhotoCdo) {
		return new ResponseEntity<>(travelLogic.registerPhoto(travelId, travelPhotoCdo), HttpStatus.OK);
	}

	@Override
	@GetMapping("/{travelId}/photos")
	public ResponseEntity<List<TravelPhotoRdo>> getPhotos(@PathVariable("travelId") String travelId) {
		return new ResponseEntity<>(travelLogic.getPhotos(travelId), HttpStatus.OK);
	}

	@Override
	@GetMapping("/{travelId}/days/{travelDayId}/photos")
	public ResponseEntity<List<TravelPhotoRdo>> getDayPhotos(@PathVariable("travelId") String travelId,
		@PathVariable("travelDayId") String travelDayId) {
		return new ResponseEntity<>(travelLogic.getDayPhotos(travelId, travelDayId), HttpStatus.OK);
	}

	@Override
	@GetMapping("/{travelId}/places/{placeId}/photos")
	public ResponseEntity<List<TravelPhotoRdo>> getPlacePhotos(@PathVariable("travelId") String travelId,
		@PathVariable("placeId") String placeId) {
		return new ResponseEntity<>(travelLogic.getPlacePhotos(travelId, placeId), HttpStatus.OK);
	}

	@Override
	@DeleteMapping("/{travelId}/photos/{photoId}")
	public ResponseEntity<Void> deletePhoto(@PathVariable("travelId") String travelId,
		@PathVariable("photoId") String photoId) {
		travelLogic.deletePhoto(travelId, photoId);
		return ResponseEntity.noContent().build();
	}

	@Override
	@PostMapping("/{travelId}/tags")
	public ResponseEntity<TravelTagRdo> registerTag(@PathVariable("travelId") String travelId,
		@RequestBody TravelTagCdo travelTagCdo) {
		return new ResponseEntity<>(travelLogic.registerTag(travelId, travelTagCdo), HttpStatus.OK);
	}

	@Override
	@DeleteMapping("/{travelId}/tags/{tagId}")
	public ResponseEntity<Void> deleteTag(@PathVariable("travelId") String travelId,
		@PathVariable("tagId") String tagId) {
		travelLogic.deleteTag(travelId, tagId);
		return ResponseEntity.noContent().build();
	}

	@Override
	@PutMapping("/{travelId}/review")
	public ResponseEntity<TravelReviewRdo> putReview(@PathVariable("travelId") String travelId,
		@RequestBody TravelReviewUdo travelReviewUdo) {
		return new ResponseEntity<>(travelLogic.putReview(travelId, travelReviewUdo), HttpStatus.OK);
	}
}
