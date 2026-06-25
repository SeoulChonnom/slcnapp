package com.seoulchonnom.aggregate.travel.store.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seoulchonnom.aggregate.travel.store.jpo.TravelReviewJpo;

public interface TravelReviewRepository extends JpaRepository<TravelReviewJpo, String> {
	Optional<TravelReviewJpo> findByTravelId(String travelId);
}
