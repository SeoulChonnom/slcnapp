package com.seoulchonnom.aggregate.calendar.store.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seoulchonnom.aggregate.calendar.store.jpo.CalendarJpo;

public interface CalendarRepository extends JpaRepository<CalendarJpo, String> {
	Optional<CalendarJpo> findById(String id);

	List<CalendarJpo> findAllByVisibleTrueOrderBySortOrderAscRegisteredTimeAsc();

	boolean existsByIdAndVisibleTrue(String id);
}
