package com.seoulchonnom.spec.travel.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

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

public interface TravelFacade {
	ResponseEntity<List<TravelRdo>> getTravels();

	ResponseEntity<TravelDetailRdo> getTravel(String travelId);

	ResponseEntity<TravelDetailRdo> registerTravel(TravelCdo travelCdo);

	ResponseEntity<TravelDetailRdo> modifyTravel(String travelId, TravelUdo travelUdo);

	ResponseEntity<Void> deleteTravel(String travelId);

	ResponseEntity<TravelDayRdo> modifyDay(String travelId, String travelDayId, TravelDayUdo travelDayUdo);

	ResponseEntity<TravelPlaceRdo> registerPlace(String travelId, String travelDayId, TravelPlaceCdo travelPlaceCdo);

	ResponseEntity<TravelPlaceRdo> modifyPlace(String travelId, String travelDayId, String placeId,
		TravelPlaceUdo travelPlaceUdo);

	ResponseEntity<Void> deletePlace(String travelId, String travelDayId, String placeId);

	ResponseEntity<TravelPhotoRdo> registerPhoto(String travelId, TravelPhotoCdo travelPhotoCdo);

	ResponseEntity<List<TravelPhotoRdo>> getPhotos(String travelId);

	ResponseEntity<List<TravelPhotoRdo>> getDayPhotos(String travelId, String travelDayId);

	ResponseEntity<List<TravelPhotoRdo>> getPlacePhotos(String travelId, String placeId);

	ResponseEntity<Void> deletePhoto(String travelId, String photoId);

	ResponseEntity<TravelTagRdo> registerTag(String travelId, TravelTagCdo travelTagCdo);

	ResponseEntity<Void> deleteTag(String travelId, String tagId);

	ResponseEntity<TravelReviewRdo> putReview(String travelId, TravelReviewUdo travelReviewUdo);
}
