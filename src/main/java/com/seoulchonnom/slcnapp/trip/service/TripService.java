package com.seoulchonnom.slcnapp.trip.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.slcnapp.trip.dto.TripListResponse;
import com.seoulchonnom.slcnapp.trip.repository.TripRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class TripService {
	private final TripRepository tripRepository;

	public List<TripListResponse> getAllTripList() {
		return tripRepository.findAll().stream()
			.map(TripListResponse::from)
			.collect(Collectors.toList());
	}
}
