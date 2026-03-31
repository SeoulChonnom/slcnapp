package com.seoulchonnom.rest.trip;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.seoulchonnom.aggregate.trip.logic.TripLogic;
import com.seoulchonnom.spec.trip.facade.sdo.TripCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripInfoRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripListRdo;

class TripResourceTest {
	@Test
	void getAllTrips_shouldDelegateToTripLogic() {
		TripLogic tripLogic = mock(TripLogic.class);
		TripResource tripResource = new TripResource(tripLogic);
		List<TripListRdo> tripList = List.of(new TripListRdo());
		when(tripLogic.getAllTripList()).thenReturn(tripList);

		ResponseEntity<List<TripListRdo>> response = tripResource.getAllTrips();

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(tripList, response.getBody());
	}

	@Test
	void getTripByDate_shouldUseDateQueryParameter() {
		TripLogic tripLogic = mock(TripLogic.class);
		TripResource tripResource = new TripResource(tripLogic);
		TripInfoRdo tripInfoRdo = new TripInfoRdo();
		when(tripLogic.getTripInfoByDate("2026-03-31")).thenReturn(tripInfoRdo);

		ResponseEntity<TripInfoRdo> response = tripResource.getTripByDate("2026-03-31");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(tripInfoRdo, response.getBody());
		verify(tripLogic).getTripInfoByDate("2026-03-31");
	}

	@Test
	void createTrip_shouldReturnCreatedTripInfo() {
		TripLogic tripLogic = mock(TripLogic.class);
		TripResource tripResource = new TripResource(tripLogic);
		TripInfoRdo tripInfoRdo = new TripInfoRdo();
		TripCdo tripCdo = new TripCdo();
		when(tripLogic.registerTrip(tripCdo)).thenReturn(tripInfoRdo);

		ResponseEntity<TripInfoRdo> response = tripResource.createTrip(tripCdo);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(tripInfoRdo, response.getBody());
	}
}
