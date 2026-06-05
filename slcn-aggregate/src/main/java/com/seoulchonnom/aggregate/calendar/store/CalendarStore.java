package com.seoulchonnom.aggregate.calendar.store;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.calendar.exception.CalendarNotFoundException;
import com.seoulchonnom.aggregate.calendar.store.mapper.CalendarJpoMapper;
import com.seoulchonnom.aggregate.calendar.store.repository.CalendarRepository;
import com.seoulchonnom.spec.calendar.entity.Calendar;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarStore {
	private final CalendarRepository calendarRepository;
	private final CalendarJpoMapper calendarJpoMapper;

	public void save(Calendar calendar) {
		calendarRepository.save(calendarJpoMapper.toJpo(calendar));
	}

	public void delete(Calendar calendar) {
		calendarRepository.delete(calendarJpoMapper.toJpo(calendar));
	}

	public Calendar findById(String id) {
		return calendarJpoMapper.toDomain(calendarRepository.findById(id).orElseThrow(CalendarNotFoundException::new));
	}

	public List<Calendar> findAllVisible() {
		return calendarRepository.findAllByVisibleTrueOrderBySortOrderAscRegisteredTimeAsc()
			.stream()
			.map(calendarJpoMapper::toDomain)
			.toList();
	}

	public boolean existsVisibleById(String id) {
		return calendarRepository.existsByIdAndVisibleTrue(id);
	}
}
