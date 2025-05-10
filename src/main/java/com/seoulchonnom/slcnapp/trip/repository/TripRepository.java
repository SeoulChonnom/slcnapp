package com.seoulchonnom.slcnapp.trip.repository;

import com.seoulchonnom.slcnapp.trip.domain.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Integer> {
	List<Trip> findAllByOrderByDateDesc();

	Optional<Trip> findByDate(@NonNull String date);
}
