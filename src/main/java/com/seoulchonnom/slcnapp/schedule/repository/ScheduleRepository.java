package com.seoulchonnom.slcnapp.schedule.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.seoulchonnom.slcnapp.schedule.domain.Schedule;

public interface ScheduleRepository extends JpaRepository<Schedule, String> {

	List<Schedule> findAllByStartBetweenAndIsVisible(LocalDateTime startDateAfter, LocalDateTime startDateBefore, boolean isVisible);
}
