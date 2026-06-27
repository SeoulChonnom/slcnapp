package com.seoulchonnom.aggregate.travel.store.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seoulchonnom.aggregate.travel.store.jpo.TravelTagJpo;

public interface TravelTagRepository extends JpaRepository<TravelTagJpo, String> {
	List<TravelTagJpo> findAllByTravelIdOrderBySortOrderAscRegisteredTimeAsc(String travelId);
}
