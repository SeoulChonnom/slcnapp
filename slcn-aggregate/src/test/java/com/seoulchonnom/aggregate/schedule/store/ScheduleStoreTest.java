package com.seoulchonnom.aggregate.schedule.store;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.schedule.store.jpo.ScheduleJpo;
import com.seoulchonnom.aggregate.schedule.store.mapper.ScheduleJpoMapper;
import com.seoulchonnom.aggregate.schedule.store.repository.ScheduleRepository;
import com.seoulchonnom.spec.schedule.entity.Schedule;

class ScheduleStoreTest {
	private final ScheduleRepository repository = mock(ScheduleRepository.class);
	private final ScheduleJpoMapper scheduleJpoMapper = mock(ScheduleJpoMapper.class);
	private final ScheduleStore scheduleStore = new ScheduleStore(repository, scheduleJpoMapper);

	@Test
	void findCandidatesByDateRange_shouldMergeAndDeduplicateOneTimeAndRecurringCandidates() {
		LocalDateTime rangeStart = LocalDateTime.of(2026, 9, 1, 0, 0);
		LocalDateTime rangeEnd = LocalDateTime.of(2026, 10, 1, 0, 0);
		ScheduleJpo oneTimeFirst = scheduleJpo(
			"schedule-001",
			LocalDateTime.of(2026, 9, 2, 9, 0),
			LocalDateTime.of(2026, 9, 2, 10, 0),
			null);
		ScheduleJpo sharedOneTimeCandidate = scheduleJpo(
			"schedule-002",
			LocalDateTime.of(2026, 9, 3, 9, 0),
			LocalDateTime.of(2026, 9, 3, 10, 0),
			null);
		ScheduleJpo sharedRecurringCandidate = scheduleJpo(
			"schedule-002",
			LocalDateTime.of(2026, 9, 3, 9, 0),
			LocalDateTime.of(2026, 9, 3, 10, 0),
			"FREQ=DAILY;COUNT=1");
		ScheduleJpo recurringMaster = scheduleJpo(
			"schedule-003",
			LocalDateTime.of(2026, 1, 6, 19, 0),
			LocalDateTime.of(2026, 1, 6, 20, 0),
			"FREQ=WEEKLY;BYDAY=TU");

		when(repository.findAllByStartBeforeAndEndAfterAndHiddenFalseAndRecurrenceRuleIsNull(rangeEnd, rangeStart))
			.thenReturn(List.of(oneTimeFirst, sharedOneTimeCandidate));
		when(repository.findAllByStartBeforeAndHiddenFalseAndRecurrenceRuleIsNotNull(rangeEnd))
			.thenReturn(List.of(recurringMaster, sharedRecurringCandidate));

		Schedule oneTimeFirstDomain = schedule(oneTimeFirst.getId());
		Schedule sharedDomain = schedule(sharedRecurringCandidate.getId());
		Schedule recurringMasterDomain = schedule(recurringMaster.getId());
		when(scheduleJpoMapper.toDomain(oneTimeFirst)).thenReturn(oneTimeFirstDomain);
		when(scheduleJpoMapper.toDomain(sharedRecurringCandidate)).thenReturn(sharedDomain);
		when(scheduleJpoMapper.toDomain(recurringMaster)).thenReturn(recurringMasterDomain);

		List<Schedule> result = scheduleStore.findCandidatesByDateRange(rangeStart, rangeEnd);

		assertThat(result).extracting(Schedule::getId)
			.containsExactly("schedule-001", "schedule-002", "schedule-003");
		verify(repository).findAllByStartBeforeAndEndAfterAndHiddenFalseAndRecurrenceRuleIsNull(rangeEnd, rangeStart);
		verify(repository).findAllByStartBeforeAndHiddenFalseAndRecurrenceRuleIsNotNull(rangeEnd);
		verify(scheduleJpoMapper).toDomain(oneTimeFirst);
		verify(scheduleJpoMapper).toDomain(sharedRecurringCandidate);
		verify(scheduleJpoMapper).toDomain(recurringMaster);
		verifyNoMoreInteractions(repository, scheduleJpoMapper);
	}

	@Test
	void findAllNonHiddenForFeed_shouldMapRepositoryOrderedFullDataset() {
		ScheduleJpo oldOneTime = scheduleJpo(
			"schedule-001",
			LocalDateTime.of(2025, 1, 1, 9, 0),
			LocalDateTime.of(2025, 1, 1, 10, 0),
			null);
		ScheduleJpo recurrenceMaster = scheduleJpo(
			"schedule-002",
			LocalDateTime.of(2026, 9, 1, 9, 0),
			LocalDateTime.of(2026, 9, 1, 10, 0),
			"FREQ=WEEKLY;BYDAY=TU");
		Schedule oldOneTimeDomain = schedule(oldOneTime.getId());
		Schedule recurrenceMasterDomain = schedule(recurrenceMaster.getId());
		when(repository.findAllByHiddenFalseOrderByStartAscIdAsc()).thenReturn(List.of(oldOneTime, recurrenceMaster));
		when(scheduleJpoMapper.toDomain(oldOneTime)).thenReturn(oldOneTimeDomain);
		when(scheduleJpoMapper.toDomain(recurrenceMaster)).thenReturn(recurrenceMasterDomain);

		List<Schedule> result = scheduleStore.findAllNonHiddenForFeed();

		assertThat(result).containsExactly(oldOneTimeDomain, recurrenceMasterDomain);
		verify(repository).findAllByHiddenFalseOrderByStartAscIdAsc();
		verify(scheduleJpoMapper).toDomain(oldOneTime);
		verify(scheduleJpoMapper).toDomain(recurrenceMaster);
		verifyNoMoreInteractions(repository, scheduleJpoMapper);
	}

	@Test
	void existsByCalendarId_shouldDelegateToRepository() {
		when(repository.existsByCalendarId("calendar-001")).thenReturn(true);

		assertThat(scheduleStore.existsByCalendarId("calendar-001")).isTrue();

		verify(repository).existsByCalendarId("calendar-001");
		verifyNoMoreInteractions(repository, scheduleJpoMapper);
	}

	private ScheduleJpo scheduleJpo(String id, LocalDateTime start, LocalDateTime end, String recurrenceRule) {
		ScheduleJpo scheduleJpo = new ScheduleJpo();
		scheduleJpo.setId(id);
		scheduleJpo.setStart(start);
		scheduleJpo.setEnd(end);
		scheduleJpo.setRecurrenceRule(recurrenceRule);
		return scheduleJpo;
	}

	private Schedule schedule(String id) {
		Schedule schedule = Schedule.builder().build();
		schedule.setId(id);
		return schedule;
	}
}
