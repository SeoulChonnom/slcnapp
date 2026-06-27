package com.seoulchonnom.aggregate.travel.store.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seoulchonnom.aggregate.travel.store.jpo.TravelPhotoJpo;

public interface TravelPhotoRepository extends JpaRepository<TravelPhotoJpo, String> {
	List<TravelPhotoJpo> findAllByTravelIdOrderByTravelDayIdAscTravelPlaceIdAscSortOrderAsc(String travelId);

	List<TravelPhotoJpo> findAllByTravelIdAndTravelDayIdIsNullAndTravelPlaceIdIsNull(String travelId);

	List<TravelPhotoJpo> findAllByTravelDayIdIn(Collection<String> travelDayIds);

	boolean existsByTravelDayIdIn(Collection<String> travelDayIds);
}
