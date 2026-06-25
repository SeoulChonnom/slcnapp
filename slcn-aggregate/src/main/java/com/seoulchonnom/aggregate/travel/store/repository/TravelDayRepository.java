package com.seoulchonnom.aggregate.travel.store.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seoulchonnom.aggregate.travel.store.jpo.TravelDayJpo;

public interface TravelDayRepository extends JpaRepository<TravelDayJpo, String> {
	List<TravelDayJpo> findAllByTravelIdOrderBySortOrderAsc(String travelId);

	List<TravelDayJpo> findAllByTravelIdAndDateIn(String travelId, Collection<LocalDate> dates);
}
