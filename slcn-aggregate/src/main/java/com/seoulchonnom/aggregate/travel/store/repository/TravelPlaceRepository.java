package com.seoulchonnom.aggregate.travel.store.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seoulchonnom.aggregate.travel.store.jpo.TravelPlaceJpo;

public interface TravelPlaceRepository extends JpaRepository<TravelPlaceJpo, String> {
	List<TravelPlaceJpo> findAllByTravelIdOrderByTravelDayIdAscSortOrderAsc(String travelId);

	List<TravelPlaceJpo> findAllByTravelDayIdIn(Collection<String> travelDayIds);

	boolean existsByTravelDayIdIn(Collection<String> travelDayIds);

	int countByTravelDayId(String travelDayId);
}
