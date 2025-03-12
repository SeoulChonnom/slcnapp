package com.seoulchonnom.slcnapp.schedule.repository;

import com.seoulchonnom.slcnapp.schedule.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, String> {

    List<Schedule> findAllByStartBetween(LocalDateTime startDateAfter, LocalDateTime startDateBefore);
}
