package com.seoulchonnom.aggregate.schedule.feed.flow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import com.seoulchonnom.aggregate.calendar.store.CalendarStore;
import com.seoulchonnom.aggregate.schedule.feed.logic.ScheduleFeedTokenLogic;
import com.seoulchonnom.aggregate.schedule.store.ScheduleStore;
import com.seoulchonnom.spec.calendar.entity.Calendar;
import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedEvent;

class ScheduleFeedFlowTest {
	private final ScheduleFeedTokenLogic feedTokenLogic = mock(ScheduleFeedTokenLogic.class);
	private final ScheduleStore scheduleStore = mock(ScheduleStore.class);
	private final CalendarStore calendarStore = mock(CalendarStore.class);
	private final ScheduleFeedFlow scheduleFeedFlow = new ScheduleFeedFlow(feedTokenLogic, scheduleStore, calendarStore);

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(scheduleFeedFlow, "windowPastMonths", 12);
		ReflectionTestUtils.setField(scheduleFeedFlow, "windowFutureMonths", 24);
	}

	@Test
	void feedWindow_shouldSnapBoundariesToMonthStart() {
		ScheduleFeedFlow.FeedWindow window = scheduleFeedFlow.feedWindow();

		assertThat(window.start().getDayOfMonth()).isEqualTo(1);
		assertThat(window.start().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
		assertThat(window.end().getDayOfMonth()).isEqualTo(1);
		assertThat(window.end().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
		assertThat(window.start()).isBefore(window.end());
	}

	@Test
	void getFeedEvents_shouldValidateBeforeLoadingSchedulesOrCalendars() {
		String rawToken = "invalid-token";
		doThrow(new RuntimeException("invalid token")).when(feedTokenLogic).validate(rawToken);

		assertThatThrownBy(() -> scheduleFeedFlow.getFeedEvents(rawToken))
			.isInstanceOf(RuntimeException.class)
			.hasMessage("invalid token");

		verify(feedTokenLogic).validate(rawToken);
		verifyNoInteractions(scheduleStore, calendarStore);
	}

	@Test
	void getFeedEvents_shouldBulkLoadCalendarsDropOrphansIgnoreVisibilityAndSortByStartThenId() {
		String rawToken = "valid-token";
		Schedule later = schedule("schedule-002", "calendar-002", LocalDateTime.of(2026, 9, 3, 9, 0));
		Schedule earlierWithHigherId = schedule("schedule-003", "calendar-001", LocalDateTime.of(2026, 9, 2, 9, 0));
		Schedule earlierWithLowerId = schedule("schedule-001", "calendar-001", LocalDateTime.of(2026, 9, 2, 9, 0));
		Schedule orphan = schedule("schedule-004", "calendar-missing", LocalDateTime.of(2026, 9, 1, 9, 0));
		Calendar invisibleCalendar = calendar("calendar-001", false);
		Calendar visibleCalendar = calendar("calendar-002", true);
		when(scheduleStore.findFeedCandidates(any(), any())).thenReturn(List.of(later, earlierWithHigherId, orphan, earlierWithLowerId));
		when(calendarStore.findAllByIds(Set.of("calendar-001", "calendar-002", "calendar-missing")))
			.thenReturn(Map.of("calendar-001", invisibleCalendar, "calendar-002", visibleCalendar));

		List<ScheduleFeedEvent> result = scheduleFeedFlow.getFeedEvents(rawToken);

		assertThat(result).extracting(event -> event.schedule().getId())
			.containsExactly("schedule-001", "schedule-003", "schedule-002");
		assertThat(result).extracting(ScheduleFeedEvent::calendar)
			.containsExactly(invisibleCalendar, invisibleCalendar, visibleCalendar);
		verify(feedTokenLogic).validate(rawToken);
		verify(scheduleStore).findFeedCandidates(any(), any());
		verify(calendarStore).findAllByIds(Set.of("calendar-001", "calendar-002", "calendar-missing"));
		verifyNoMoreInteractions(feedTokenLogic, scheduleStore, calendarStore);
	}

	@Test
	void getFeedEvents_shouldReturnEmptyWithoutCalendarLookupWhenNoSchedulesExist() {
		String rawToken = "valid-token";
		when(scheduleStore.findFeedCandidates(any(), any())).thenReturn(List.of());

		assertThat(scheduleFeedFlow.getFeedEvents(rawToken)).isEmpty();

		InOrder inOrder = inOrder(feedTokenLogic, scheduleStore);
		inOrder.verify(feedTokenLogic).validate(rawToken);
		inOrder.verify(scheduleStore).findFeedCandidates(any(), any());
		verifyNoInteractions(calendarStore);
	}

	@Test
	void getFeedEvents_shouldFilterNullCalendarIdsBeforeBulkLookupAndDropOrphans() {
		String rawToken = "valid-token";
		Schedule valid = schedule("schedule-001", "calendar-001", LocalDateTime.of(2026, 9, 2, 9, 0));
		Schedule nullCalendar = schedule("schedule-002", null, LocalDateTime.of(2026, 9, 1, 9, 0));
		when(scheduleStore.findFeedCandidates(any(), any())).thenReturn(List.of(nullCalendar, valid));
		Calendar calendar = calendar("calendar-001", true);
		when(calendarStore.findAllByIds(Set.of("calendar-001"))).thenReturn(Map.of("calendar-001", calendar));

		assertThat(scheduleFeedFlow.getFeedEvents(rawToken))
			.extracting(event -> event.schedule().getId())
			.containsExactly("schedule-001");

		verify(calendarStore).findAllByIds(Set.of("calendar-001"));
		verifyNoMoreInteractions(calendarStore);
	}

	private Schedule schedule(String id, String calendarId, LocalDateTime start) {
		Schedule schedule = Schedule.builder()
			.calendarId(calendarId)
			.start(start)
			.end(start.plusHours(1))
			.build();
		schedule.setId(id);
		return schedule;
	}

	private Calendar calendar(String id, boolean visible) {
		Calendar calendar = Calendar.builder().visible(visible).build();
		calendar.setId(id);
		return calendar;
	}
}
