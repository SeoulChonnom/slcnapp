package com.seoulchonnom.aggregate.trip.store.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seoulchonnom.aggregate.trip.store.jpo.TripJpo;

public interface TripRepository extends JpaRepository<TripJpo, String> {
	List<TripJpo> findAllByOrderByDateDesc();

	Optional<TripJpo> findById(String id);
}
