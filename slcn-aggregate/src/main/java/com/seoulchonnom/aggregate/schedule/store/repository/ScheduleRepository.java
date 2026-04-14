package com.seoulchonnom.aggregate.schedule.store.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seoulchonnom.aggregate.schedule.store.jpo.ScheduleJpo;

public interface ScheduleRepository extends JpaRepository<ScheduleJpo, String> {
	Optional<ScheduleJpo> findById(String id);

	List<ScheduleJpo> findAllByStartBeforeAndEndAfterAndHiddenFalse(LocalDateTime rangeEnd, LocalDateTime rangeStart);
}
