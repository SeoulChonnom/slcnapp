package com.seoulchonnom.aggregate.calendar.store;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.seoulchonnom.aggregate.calendar.store.jpo.CalendarJpo;
import com.seoulchonnom.aggregate.calendar.store.mapper.CalendarJpoMapper;
import com.seoulchonnom.aggregate.calendar.store.repository.CalendarRepository;
import com.seoulchonnom.spec.calendar.entity.Calendar;

class CalendarStoreTest {
	private final CalendarRepository calendarRepository = mock(CalendarRepository.class);
	private final CalendarJpoMapper calendarJpoMapper = mock(CalendarJpoMapper.class);
	private final CalendarStore calendarStore = new CalendarStore(calendarRepository, calendarJpoMapper);

	@Test
	void findAllByIds_shouldBulkLoadAllCalendarsIncludingInvisibleOnes() {
		Set<String> calendarIds = Set.of("calendar-001", "calendar-002");
		CalendarJpo visibleJpo = calendarJpo("calendar-001");
		CalendarJpo hiddenJpo = calendarJpo("calendar-002");
		Calendar visibleCalendar = calendar(visibleJpo.getId(), true);
		Calendar hiddenCalendar = calendar(hiddenJpo.getId(), false);
		when(calendarRepository.findAllByIdIn(calendarIds)).thenReturn(List.of(visibleJpo, hiddenJpo));
		when(calendarJpoMapper.toDomain(visibleJpo)).thenReturn(visibleCalendar);
		when(calendarJpoMapper.toDomain(hiddenJpo)).thenReturn(hiddenCalendar);

		Map<String, Calendar> result = calendarStore.findAllByIds(calendarIds);

		assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
			"calendar-001", visibleCalendar,
			"calendar-002", hiddenCalendar));
		verify(calendarRepository).findAllByIdIn(calendarIds);
		verify(calendarJpoMapper).toDomain(visibleJpo);
		verify(calendarJpoMapper).toDomain(hiddenJpo);
		verifyNoMoreInteractions(calendarRepository, calendarJpoMapper);
	}

	@Test
	void delete_shouldFlushImmediatelySoConstraintViolationsSurfaceSynchronously() {
		Calendar calendar = calendar("CALENDAR-0001", true);
		CalendarJpo jpo = calendarJpo("CALENDAR-0001");
		when(calendarJpoMapper.toJpo(calendar)).thenReturn(jpo);

		calendarStore.delete(calendar);

		InOrder inOrder = inOrder(calendarRepository);
		inOrder.verify(calendarRepository).delete(jpo);
		inOrder.verify(calendarRepository).flush();
	}

	private CalendarJpo calendarJpo(String id) {
		CalendarJpo jpo = new CalendarJpo();
		jpo.setId(id);
		return jpo;
	}

	private Calendar calendar(String id, boolean visible) {
		Calendar calendar = Calendar.builder().visible(visible).build();
		calendar.setId(id);
		return calendar;
	}
}
