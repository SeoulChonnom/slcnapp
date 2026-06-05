package com.seoulchonnom.aggregate.trip.store.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.seoulchonnom.aggregate.trip.store.jpo.TripJpo;

public interface TripRepository extends JpaRepository<TripJpo, String> {
	@EntityGraph(attributePaths = {"quiz"})
	List<TripJpo> findAllByOrderByDateDesc();

	@Override
	@EntityGraph(attributePaths = {"quiz", "quiz.options"})
	Optional<TripJpo> findById(String id);
}
