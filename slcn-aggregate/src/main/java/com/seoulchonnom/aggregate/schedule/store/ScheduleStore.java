package com.seoulchonnom.aggregate.schedule.store;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.schedule.exception.ScheduleNotFoundException;
import com.seoulchonnom.aggregate.schedule.store.jpo.ScheduleJpo;
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

	public List<Schedule> findCandidatesByDateRange(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
		Map<String, ScheduleJpo> candidates = new LinkedHashMap<>();
		scheduleRepository.findAllByStartBeforeAndEndAfterAndRecurrenceRuleIsNull(rangeEnd, rangeStart)
			.forEach(scheduleJpo -> candidates.put(scheduleJpo.getId(), scheduleJpo));
		scheduleRepository.findAllByStartBeforeAndRecurrenceRuleIsNotNull(rangeEnd)
			.forEach(scheduleJpo -> candidates.put(scheduleJpo.getId(), scheduleJpo));
		return candidates.values().stream()
			.map(scheduleJpoMapper::toDomain)
			.toList();
	}

	public List<Schedule> findFeedCandidates(LocalDateTime windowStart, LocalDateTime windowEnd) {
		Map<String, ScheduleJpo> candidates = new LinkedHashMap<>();
		scheduleRepository
			.findAllByStartBeforeAndEndAfterAndRecurrenceRuleIsNullOrderByStartAscIdAsc(windowEnd, windowStart)
			.forEach(scheduleJpo -> candidates.put(scheduleJpo.getId(), scheduleJpo));
		scheduleRepository
			.findAllByStartBeforeAndRecurrenceRuleIsNotNullOrderByStartAscIdAsc(windowEnd)
			.forEach(scheduleJpo -> candidates.put(scheduleJpo.getId(), scheduleJpo));
		return candidates.values().stream()
			.map(scheduleJpoMapper::toDomain)
			.toList();
	}

	public boolean existsByCalendarId(String calendarId) {
		return scheduleRepository.existsByCalendarId(calendarId);
	}

}
