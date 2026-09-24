package com.seoulchonnom.aggregate.schedule.logic;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.entity.ScheduleOccurrence;

class ScheduleRecurrenceExpanderTest {
	private final ScheduleRecurrenceExpander expander =
		new ScheduleRecurrenceExpander(new ScheduleRecurrenceRuleValidator());

	@Test
	void expand_shouldIncludeWeeklyOccurrencesWhenMasterStartsBeforeRange() {
		Schedule schedule = recurringSchedule(
			"2026-01-06T19:00:00+09:00",
			"2026-01-06T20:00:00+09:00",
			"FREQ=WEEKLY;BYDAY=TU");
		schedule.setId("schedule-1");

		List<ScheduleOccurrence> result = expander.expand(
			schedule,
			LocalDateTime.of(2026, 9, 1, 0, 0),
			LocalDateTime.of(2026, 10, 1, 0, 0));

		assertThat(result).extracting(ScheduleOccurrence::start)
			.containsExactly(
				LocalDateTime.of(2026, 9, 1, 19, 0),
				LocalDateTime.of(2026, 9, 8, 19, 0),
				LocalDateTime.of(2026, 9, 15, 19, 0),
				LocalDateTime.of(2026, 9, 22, 19, 0),
				LocalDateTime.of(2026, 9, 29, 19, 0));
	}

	@Test
	void expand_shouldPreserveMasterDurationAndGenerateSeoulOccurrenceId() {
		Schedule schedule = recurringSchedule(
			"2026-09-01T19:00:00+09:00",
			"2026-09-01T21:30:00+09:00",
			"FREQ=DAILY;COUNT=2");
		schedule.setId("schedule-2");

		List<ScheduleOccurrence> result = expander.expand(
			schedule,
			LocalDateTime.of(2026, 9, 1, 0, 0),
			LocalDateTime.of(2026, 9, 3, 0, 0));

		assertThat(result).extracting(ScheduleOccurrence::start)
			.containsExactly(
				LocalDateTime.of(2026, 9, 1, 19, 0),
				LocalDateTime.of(2026, 9, 2, 19, 0));
		assertThat(result).extracting(ScheduleOccurrence::end)
			.containsExactly(
				LocalDateTime.of(2026, 9, 1, 21, 30),
				LocalDateTime.of(2026, 9, 2, 21, 30));
		assertThat(result).extracting(ScheduleOccurrence::occurrenceId)
			.containsExactly(
				"schedule-2/2026-09-01T19:00:00+09:00",
				"schedule-2/2026-09-02T19:00:00+09:00");
	}

	@Test
	void expand_shouldUseHalfOpenOverlapBoundaries() {
		Schedule endingAtRangeStart = recurringSchedule(
			"2026-09-01T09:00:00+09:00",
			"2026-09-01T10:00:00+09:00",
			"FREQ=DAILY;COUNT=1");
		Schedule startingAtRangeEnd = recurringSchedule(
			"2026-09-01T11:00:00+09:00",
			"2026-09-01T12:00:00+09:00",
			"FREQ=DAILY;COUNT=1");
		Schedule spanningRangeStart = recurringSchedule(
			"2026-09-01T09:00:00+09:00",
			"2026-09-01T11:00:00+09:00",
			"FREQ=DAILY;COUNT=1");

		LocalDateTime rangeStart = LocalDateTime.of(2026, 9, 1, 10, 0);
		LocalDateTime rangeEnd = LocalDateTime.of(2026, 9, 11, 0, 0);

		assertThat(expander.expand(endingAtRangeStart, rangeStart, rangeEnd)).isEmpty();
		assertThat(expander.expand(startingAtRangeEnd, rangeStart, LocalDateTime.of(2026, 9, 1, 11, 0)))
			.isEmpty();
		assertThat(expander.expand(spanningRangeStart, rangeStart, rangeEnd))
			.hasSize(1);
	}

	@Test
	void expand_shouldHonorCountWhenRequestedRangeStartsAfterMaster() {
		Schedule schedule = recurringSchedule(
			"2026-09-01T09:00:00+09:00",
			"2026-09-01T10:00:00+09:00",
			"FREQ=DAILY;COUNT=3");

		List<ScheduleOccurrence> result = expander.expand(
			schedule,
			LocalDateTime.of(2026, 9, 3, 0, 0),
			LocalDateTime.of(2026, 9, 6, 0, 0));

		assertThat(result).extracting(ScheduleOccurrence::start)
			.containsExactly(LocalDateTime.of(2026, 9, 3, 9, 0));
	}

	@Test
	void expand_shouldConvertTimedUntilFromUtcIntoSeoulTime() {
		Schedule schedule = recurringSchedule(
			"2026-09-01T09:00:00+09:00",
			"2026-09-01T10:00:00+09:00",
			"FREQ=DAILY;UNTIL=20260902T003000Z");

		List<ScheduleOccurrence> result = expander.expand(
			schedule,
			LocalDateTime.of(2026, 9, 1, 0, 0),
			LocalDateTime.of(2026, 9, 5, 0, 0));

		assertThat(result).extracting(ScheduleOccurrence::start)
			.containsExactly(
				LocalDateTime.of(2026, 9, 1, 9, 0),
				LocalDateTime.of(2026, 9, 2, 9, 0));
	}

	@Test
	void expand_shouldGenerateMonthlyOccurrencesUsingByMonthDay() {
		Schedule schedule = recurringSchedule(
			"2026-01-15T09:00:00+09:00",
			"2026-01-15T10:00:00+09:00",
			"FREQ=MONTHLY;BYMONTHDAY=15");

		List<ScheduleOccurrence> result = expander.expand(
			schedule,
			LocalDateTime.of(2026, 3, 1, 0, 0),
			LocalDateTime.of(2026, 6, 1, 0, 0));

		assertThat(result).extracting(ScheduleOccurrence::start)
			.containsExactly(
				LocalDateTime.of(2026, 3, 15, 9, 0),
				LocalDateTime.of(2026, 4, 15, 9, 0),
				LocalDateTime.of(2026, 5, 15, 9, 0));
	}

	@Test
	void expand_shouldGenerateYearlyOccurrences() {
		Schedule schedule = recurringSchedule(
			"2024-03-15T09:00:00+09:00",
			"2024-03-15T10:00:00+09:00",
			"FREQ=YEARLY");

		List<ScheduleOccurrence> result = expander.expand(
			schedule,
			LocalDateTime.of(2026, 1, 1, 0, 0),
			LocalDateTime.of(2027, 1, 1, 0, 0));

		assertThat(result).extracting(ScheduleOccurrence::start)
			.containsExactly(LocalDateTime.of(2026, 3, 15, 9, 0));
	}

	@Test
	void expand_shouldPreserveAllDayExclusiveEndDateAndCount() {
		Schedule schedule = recurringSchedule(
			"2026-04-01",
			"2026-04-03",
			"FREQ=DAILY;COUNT=2");
		schedule.setAllDay(true);

		List<ScheduleOccurrence> result = expander.expand(
			schedule,
			LocalDateTime.of(2026, 4, 2, 0, 0),
			LocalDateTime.of(2026, 4, 4, 0, 0));

		assertThat(result).extracting(ScheduleOccurrence::start)
			.containsExactly(
				LocalDateTime.of(2026, 4, 1, 0, 0),
				LocalDateTime.of(2026, 4, 2, 0, 0));
		assertThat(result).extracting(ScheduleOccurrence::end)
			.containsExactly(
				LocalDateTime.of(2026, 4, 3, 0, 0),
				LocalDateTime.of(2026, 4, 4, 0, 0));
	}

	@Test
	void expand_shouldReturnNonRecurringScheduleOnlyWhenItOverlaps() {
		Schedule schedule = Schedule.builder()
			.start(LocalDateTime.of(2026, 9, 1, 9, 0))
			.end(LocalDateTime.of(2026, 9, 1, 10, 0))
			.build();

		assertThat(expander.expand(schedule,
			LocalDateTime.of(2026, 9, 1, 10, 0),
			LocalDateTime.of(2026, 9, 1, 11, 0))).isEmpty();
		assertThat(expander.expand(schedule,
			LocalDateTime.of(2026, 9, 1, 9, 30),
			LocalDateTime.of(2026, 9, 1, 11, 0)))
			.extracting(ScheduleOccurrence::occurrenceId)
			.containsExactly((String) null);
	}

	private Schedule recurringSchedule(String start, String end, String recurrenceRule) {
		boolean allDay = start.length() == 10;
		return Schedule.builder()
			.start(allDay ? LocalDateTime.parse(start + "T00:00:00") : parseSeoul(start))
			.end(allDay ? LocalDateTime.parse(end + "T00:00:00") : parseSeoul(end))
			.recurrenceRule(recurrenceRule)
			.allDay(allDay)
			.build();
	}

	private LocalDateTime parseSeoul(String value) {
		return java.time.OffsetDateTime.parse(value).toLocalDateTime();
	}
}
