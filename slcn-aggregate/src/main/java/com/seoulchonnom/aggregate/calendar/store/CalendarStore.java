package com.seoulchonnom.aggregate.calendar.store;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

	@Transactional
	public void save(Calendar calendar) {
		calendarRepository.save(calendarJpoMapper.toJpo(calendar));
	}

	@Transactional
	public void delete(Calendar calendar) {
		calendarRepository.delete(calendarJpoMapper.toJpo(calendar));
		// fk_schedule_calendar 위반을 이 트랜잭션 안에서 즉시 드러내려면 커밋까지 flush를
		// 미루면 안 된다. flush를 미루면 예외가 CalendarLogic#deleteCalendar의 try/catch를
		// 벗어난 커밋 시점에 발생해 CalendarScheduleConflictException으로 변환되지 않는다.
		calendarRepository.flush();
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

	public Map<String, Calendar> findAllByIds(Collection<String> ids) {
		Map<String, Calendar> calendars = new LinkedHashMap<>();
		calendarRepository.findAllByIdIn(ids).stream()
			.map(calendarJpoMapper::toDomain)
			.forEach(calendar -> calendars.put(calendar.getId(), calendar));
		return calendars;
	}

	public boolean existsVisibleById(String id) {
		return calendarRepository.existsByIdAndVisibleTrue(id);
	}
}
