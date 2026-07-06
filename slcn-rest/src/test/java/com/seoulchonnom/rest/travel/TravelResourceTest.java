package com.seoulchonnom.rest.travel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.seoulchonnom.aggregate.travel.logic.TravelLogic;
import com.seoulchonnom.spec.travel.facade.sdo.TravelCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDetailRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelRdo;
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
	void deleteCommands_shouldReturnNoContent() {
		assertEquals(HttpStatus.NO_CONTENT, travelResource.deleteTravel("travel-1").getStatusCode());

		verify(travelLogic).deleteTravel("travel-1");
	}
}
