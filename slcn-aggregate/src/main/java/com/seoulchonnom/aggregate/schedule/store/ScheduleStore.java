package com.seoulchonnom.aggregate.schedule.store;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.schedule.exception.ScheduleNotFoundException;
import com.seoulchonnom.aggregate.schedule.store.mapper.ScheduleJpoMapper;
import com.seoulchonnom.aggregate.schedule.store.repository.ScheduleRepository;
import com.seoulchonnom.spec.schedule.entity.Schedule;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ScheduleStore {
	private final ScheduleRepository scheduleRepository;
	private final ScheduleJpoMapper scheduleJpoMapper;

	public void save(Schedule schedule) {
		scheduleRepository.save(scheduleJpoMapper.toJpo(schedule));
	}

	public void delete(Schedule schedule) {
		scheduleRepository.delete(scheduleJpoMapper.toJpo(schedule));
	}

	public Schedule findById(String id) {
		return scheduleJpoMapper.toDomain(scheduleRepository.findById(id).orElseThrow(ScheduleNotFoundException::new));
	}

	public List<Schedule> findAllByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
		return scheduleRepository.findAllByStartBeforeAndEndAfterAndHiddenFalse(endDate, startDate)
			.stream().map(scheduleJpoMapper::toDomain).toList();
	}

}
