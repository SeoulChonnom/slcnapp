package com.seoulchonnom.aggregate.schedule.feed.flow;

import static com.seoulchonnom.spec.schedule.constant.ScheduleConstant.*;
import static java.util.Comparator.*;
import static java.util.stream.Collectors.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.calendar.store.CalendarStore;
import com.seoulchonnom.aggregate.schedule.feed.logic.ScheduleFeedTokenLogic;
import com.seoulchonnom.aggregate.schedule.store.ScheduleStore;
import com.seoulchonnom.spec.calendar.entity.Calendar;
import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedContent;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedEvent;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedToken;

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

	@Value("${slcn.ics.window.past-months:12}")
	private int windowPastMonths;

	@Value("${slcn.ics.window.future-months:24}")
	private int windowFutureMonths;

	public FeedWindow feedWindow() {
		if (windowPastMonths <= 0 || windowFutureMonths <= 0) {
			throw new IllegalStateException(
				"slcn.ics.window.past-months/future-months는 1 이상이어야 합니다: past-months="
					+ windowPastMonths + ", future-months=" + windowFutureMonths);
		}

		LocalDateTime monthStart = LocalDateTime.now(SCHEDULE_ZONE_ID)
			.withDayOfMonth(1)
			.toLocalDate()
			.atStartOfDay();
		FeedWindow window = new FeedWindow(
			monthStart.minusMonths(windowPastMonths),
			monthStart.plusMonths(windowFutureMonths));
		if (!window.start().isBefore(window.end())) {
			throw new IllegalStateException(
				"ICS 피드 시간 창이 역전되었습니다: start=" + window.start() + ", end=" + window.end());
		}
		return window;
	}

	public record FeedWindow(LocalDateTime start, LocalDateTime end) {
	}

	public ScheduleFeedContent getFeedContent(String rawToken) {
		ScheduleFeedToken feedToken = feedTokenLogic.validate(rawToken);

		FeedWindow window = feedWindow();
		List<Schedule> schedules = scheduleStore.findFeedCandidates(window.start(), window.end());
		if (schedules.isEmpty()) {
			return new ScheduleFeedContent(feedToken.getName(), List.of());
		}

		Set<String> calendarIds = schedules.stream()
			.map(Schedule::getCalendarId)
			.filter(Objects::nonNull)
			.collect(toSet());
		Map<String, Calendar> calendars = calendarIds.isEmpty()
			? Map.of()
			: calendarStore.findAllByIds(calendarIds);

		List<ScheduleFeedEvent> events = schedules.stream()
			.filter(schedule -> {
				String calendarId = schedule.getCalendarId();
				if (calendarId != null && calendars.containsKey(calendarId)) {
					return true;
				}
				if (calendarId == null) {
					log.warn("Skipping orphan schedule: scheduleId={}", schedule.getId());
				} else {
					log.warn("Skipping orphan schedule: scheduleId={}, calendarId={}", schedule.getId(), calendarId);
				}
				return false;
			})
			.map(schedule -> new ScheduleFeedEvent(schedule, calendars.get(schedule.getCalendarId())))
			.sorted(comparing((ScheduleFeedEvent event) -> event.schedule().getStart())
				.thenComparing(event -> event.schedule().getId()))
			.toList();

		return new ScheduleFeedContent(feedToken.getName(), events);
	}
}
