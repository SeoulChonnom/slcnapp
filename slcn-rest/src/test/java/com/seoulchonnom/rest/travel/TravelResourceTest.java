package com.seoulchonnom.rest.travel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.seoulchonnom.aggregate.travel.logic.TravelLogic;
import com.seoulchonnom.spec.travel.facade.sdo.TravelCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDayRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDayUdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDetailRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelPhotoCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelPhotoRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelPlaceCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelPlaceRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelReviewRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelReviewUdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelTagCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelTagRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelUdo;

class TravelResourceTest {
	private final TravelLogic travelLogic = mock(TravelLogic.class);
	private final TravelResource travelResource = new TravelResource(travelLogic);

	@Test
	void getTravels_shouldDelegateToTravelLogic() {
		List<TravelRdo> travels = List.of(new TravelRdo());
		when(travelLogic.getTravels()).thenReturn(travels);

		ResponseEntity<List<TravelRdo>> response = travelResource.getTravels();

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(travels, response.getBody());
		verify(travelLogic).getTravels();
	}

	@Test
	void registerAndModifyTravel_shouldReturnDetail() {
		TravelCdo cdo = new TravelCdo();
		TravelUdo udo = new TravelUdo();
		TravelDetailRdo detailRdo = new TravelDetailRdo();
		when(travelLogic.registerTravel(cdo)).thenReturn(detailRdo);
		when(travelLogic.modifyTravel("travel-1", udo)).thenReturn(detailRdo);

		ResponseEntity<TravelDetailRdo> registerResponse = travelResource.registerTravel(cdo);
		ResponseEntity<TravelDetailRdo> modifyResponse = travelResource.modifyTravel("travel-1", udo);

		assertEquals(HttpStatus.OK, registerResponse.getStatusCode());
		assertEquals(detailRdo, registerResponse.getBody());
		assertEquals(HttpStatus.OK, modifyResponse.getStatusCode());
		assertEquals(detailRdo, modifyResponse.getBody());
	}

	@Test
	void childCommands_shouldDelegateToTravelLogic() {
		TravelPlaceCdo placeCdo = new TravelPlaceCdo();
		TravelPhotoCdo photoCdo = new TravelPhotoCdo();
		TravelDayUdo dayUdo = new TravelDayUdo();
		TravelTagCdo tagCdo = new TravelTagCdo();
		TravelReviewUdo reviewUdo = new TravelReviewUdo();
		TravelDayRdo dayRdo = new TravelDayRdo();
		TravelPlaceRdo placeRdo = new TravelPlaceRdo();
		TravelPhotoRdo photoRdo = new TravelPhotoRdo();
		TravelTagRdo tagRdo = new TravelTagRdo();
		TravelReviewRdo reviewRdo = new TravelReviewRdo();
		when(travelLogic.modifyDay("travel-1", "day-1", dayUdo)).thenReturn(dayRdo);
		when(travelLogic.registerPlace("travel-1", "day-1", placeCdo)).thenReturn(placeRdo);
		when(travelLogic.registerPhoto("travel-1", photoCdo)).thenReturn(photoRdo);
		when(travelLogic.registerTag("travel-1", tagCdo)).thenReturn(tagRdo);
		when(travelLogic.putReview("travel-1", reviewUdo)).thenReturn(reviewRdo);

		assertEquals(dayRdo, travelResource.modifyDay("travel-1", "day-1", dayUdo).getBody());
		assertEquals(placeRdo, travelResource.registerPlace("travel-1", "day-1", placeCdo).getBody());
		assertEquals(photoRdo, travelResource.registerPhoto("travel-1", photoCdo).getBody());
		assertEquals(tagRdo, travelResource.registerTag("travel-1", tagCdo).getBody());
		assertEquals(reviewRdo, travelResource.putReview("travel-1", reviewUdo).getBody());
	}

	@Test
	void deleteCommands_shouldReturnNoContent() {
		assertEquals(HttpStatus.NO_CONTENT, travelResource.deleteTravel("travel-1").getStatusCode());
		assertEquals(HttpStatus.NO_CONTENT, travelResource.deletePlace("travel-1", "day-1", "place-1").getStatusCode());
		assertEquals(HttpStatus.NO_CONTENT, travelResource.deletePhoto("travel-1", "photo-1").getStatusCode());
		assertEquals(HttpStatus.NO_CONTENT, travelResource.deleteTag("travel-1", "tag-1").getStatusCode());

		verify(travelLogic).deleteTravel("travel-1");
		verify(travelLogic).deletePlace("travel-1", "day-1", "place-1");
		verify(travelLogic).deletePhoto("travel-1", "photo-1");
		verify(travelLogic).deleteTag("travel-1", "tag-1");
	}

	@Test
	void photoQueries_shouldDelegateToTravelLogic() {
		List<TravelPhotoRdo> photos = List.of(new TravelPhotoRdo());
		when(travelLogic.getPhotos("travel-1")).thenReturn(photos);
		when(travelLogic.getDayPhotos("travel-1", "day-1")).thenReturn(photos);
		when(travelLogic.getPlacePhotos("travel-1", "place-1")).thenReturn(photos);

		assertEquals(photos, travelResource.getPhotos("travel-1").getBody());
		assertEquals(photos, travelResource.getDayPhotos("travel-1", "day-1").getBody());
		assertEquals(photos, travelResource.getPlacePhotos("travel-1", "place-1").getBody());
	}
}
