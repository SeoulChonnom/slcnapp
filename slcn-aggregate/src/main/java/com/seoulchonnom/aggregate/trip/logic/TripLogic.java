package com.seoulchonnom.aggregate.trip.logic;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.common.generator.store.entity.SequenceName;
import com.seoulchonnom.aggregate.trip.store.TripStore;
import com.seoulchonnom.spec.common.generator.IdGenerator;
import com.seoulchonnom.spec.trip.entity.Trip;
import com.seoulchonnom.spec.trip.facade.sdo.TripCdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripInfoRdo;
import com.seoulchonnom.spec.trip.facade.sdo.TripListRdo;
import com.seoulchonnom.spec.trip.mapper.TripMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripLogic {
	private final TripStore tripStore;
	private final IdGenerator idGenerator;
	private final TripMapper tripMapper;

	public List<TripListRdo> getAllTripList() {
		return tripStore.findAllByOrderByDateDesc().stream()
			.map(tripMapper::toTripListRdo)
			.toList();
	}

	public TripInfoRdo getTripInfo(String id) {
		return tripMapper.toTripInfoRdo(tripStore.findById(id));
	}

	@Transactional
	public void registerTrip(TripCdo tripCdo) {
		String nextTripId = idGenerator.nextDomainId(SequenceName.TRIP.toString());
		Trip trip = new Trip(tripCdo, nextTripId);
		tripStore.saveTrip(trip);
	}
}
