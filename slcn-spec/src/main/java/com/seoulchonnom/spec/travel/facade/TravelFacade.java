package com.seoulchonnom.spec.travel.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.seoulchonnom.spec.travel.facade.sdo.TravelCdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelDetailRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelRdo;
import com.seoulchonnom.spec.travel.facade.sdo.TravelUdo;

public interface TravelFacade {
	ResponseEntity<List<TravelRdo>> getTravels();

	ResponseEntity<TravelDetailRdo> getTravel(String travelId);

	ResponseEntity<TravelDetailRdo> registerTravel(TravelCdo travelCdo);

	ResponseEntity<TravelDetailRdo> modifyTravel(String travelId, TravelUdo travelUdo);

	ResponseEntity<Void> deleteTravel(String travelId);
}
