package com.seoulchonnom.rest.trip;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.seoulchonnom.aggregate.trip.logic.TripLogic;
import com.seoulchonnom.spec.trip.facade.sdo.TripCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripDetailRdo;
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
	void getTripById_shouldUsePathVariable() {
		TripLogic tripLogic = mock(TripLogic.class);
		TripResource tripResource = new TripResource(tripLogic);
		TripDetailRdo tripDetailRdo = new TripDetailRdo();
		when(tripLogic.getTripById("TRIP-1")).thenReturn(tripDetailRdo);

		ResponseEntity<TripDetailRdo> response = tripResource.getTripById("TRIP-1");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(tripDetailRdo, response.getBody());
		verify(tripLogic).getTripById("TRIP-1");
	}

	@Test
	void createTrip_shouldReturnCreatedTripDetail() {
		TripLogic tripLogic = mock(TripLogic.class);
		TripResource tripResource = new TripResource(tripLogic);
		TripDetailRdo tripDetailRdo = new TripDetailRdo();
		TripCdo tripCdo = new TripCdo();
		when(tripLogic.registerTrip(tripCdo)).thenReturn(tripDetailRdo);

		ResponseEntity<TripDetailRdo> response = tripResource.createTrip(tripCdo);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(tripDetailRdo, response.getBody());
	}
}
