package com.seoulchonnom.rest.trip;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.seoulchonnom.aggregate.trip.logic.TripLogic;
import com.seoulchonnom.spec.trip.facade.sdo.QuizRdo;
import com.seoulchonnom.spec.trip.facade.sdo.QuizResultRdo;
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

	@Test
	void getTripQuiz_shouldDelegateToTripLogic() {
		TripLogic tripLogic = mock(TripLogic.class);
		TripResource tripResource = new TripResource(tripLogic);
		QuizRdo quizRdo = new QuizRdo();
		when(tripLogic.getTripQuiz("TRIP-1")).thenReturn(quizRdo);

		ResponseEntity<QuizRdo> response = tripResource.getTripQuiz("TRIP-1");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(quizRdo, response.getBody());
		verify(tripLogic).getTripQuiz("TRIP-1");
	}

	@Test
	void checkTripQuizAnswer_shouldDelegateToTripLogic() {
		TripLogic tripLogic = mock(TripLogic.class);
		TripResource tripResource = new TripResource(tripLogic);
		QuizResultRdo quizResultRdo = new QuizResultRdo();
		when(tripLogic.checkTripQuizAnswer("TRIP-1", "OPT-2")).thenReturn(quizResultRdo);

		ResponseEntity<QuizResultRdo> response = tripResource.checkTripQuizAnswer("TRIP-1", "OPT-2");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(quizResultRdo, response.getBody());
		verify(tripLogic).checkTripQuizAnswer("TRIP-1", "OPT-2");
	}
}
