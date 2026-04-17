package com.seoulchonnom.aggregate.trip.store;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.trip.exception.TripNotFoundException;
import com.seoulchonnom.aggregate.trip.store.mapper.TripJpoMapper;
import com.seoulchonnom.aggregate.trip.store.repository.TripRepository;
import com.seoulchonnom.spec.trip.entity.Trip;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TripStore {
	private final TripRepository tripRepository;
	private final TripJpoMapper tripJpoMapper;

	public void saveTrip(Trip trip) {
		tripRepository.save(tripJpoMapper.toJpo(trip));
	}

	public List<Trip> findAllByOrderByDateDesc() {
		return tripRepository.findAllByOrderByDateDesc().stream().map(tripJpoMapper::toDomain).toList();
	}

	public Trip findById(String id) {
		return tripJpoMapper.toDomain(tripRepository.findById(id).orElseThrow(TripNotFoundException::new));
	}
}
