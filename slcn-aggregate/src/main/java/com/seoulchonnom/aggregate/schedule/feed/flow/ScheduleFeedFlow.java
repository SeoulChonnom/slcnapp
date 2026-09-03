package com.seoulchonnom.aggregate.schedule.feed.flow;

import static java.util.Comparator.*;
import static java.util.stream.Collectors.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.calendar.store.CalendarStore;
import com.seoulchonnom.aggregate.schedule.feed.logic.ScheduleFeedTokenLogic;
import com.seoulchonnom.aggregate.schedule.store.ScheduleStore;
import com.seoulchonnom.spec.calendar.entity.Calendar;
import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ScheduleFeedFlow {
	private final ScheduleFeedTokenLogic feedTokenLogic;
	private final ScheduleStore scheduleStore;
	private final CalendarStore calendarStore;

	public List<ScheduleFeedEvent> getFeedEvents(String rawToken) {
		feedTokenLogic.validate(rawToken);

		List<Schedule> schedules = scheduleStore.findAllNonHiddenForFeed();
		Set<String> calendarIds = schedules.stream()
			.map(Schedule::getCalendarId)
			.collect(toSet());
		Map<String, Calendar> calendars = calendarStore.findAllByIds(calendarIds);

		return schedules.stream()
			.filter(schedule -> {
				if (calendars.containsKey(schedule.getCalendarId())) {
					return true;
				}
				log.warn("Skipping orphan schedule: scheduleId={}, calendarId={}", schedule.getId(), schedule.getCalendarId());
				return false;
			})
			.map(schedule -> new ScheduleFeedEvent(schedule, calendars.get(schedule.getCalendarId())))
			.sorted(comparing((ScheduleFeedEvent event) -> event.schedule().getStart())
				.thenComparing(event -> event.schedule().getId()))
			.toList();
	}
}
