package com.seoulchonnom.aggregate.schedule.logic;

import static com.seoulchonnom.spec.schedule.constant.ScheduleConstant.SCHEDULE_ZONE_ID;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import net.fortuna.ical4j.model.Recur;

import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.entity.ScheduleOccurrence;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ScheduleRecurrenceExpander {
	private static final DateTimeFormatter OCCURRENCE_ID_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private final ScheduleRecurrenceRuleValidator recurrenceRuleValidator;

	public List<ScheduleOccurrence> expand(
		Schedule schedule,
		LocalDateTime rangeStart,
		LocalDateTime rangeEnd
	) {
		Duration duration = Duration.between(schedule.getStart(), schedule.getEnd());
		if (!StringUtils.hasText(schedule.getRecurrenceRule())) {
			return overlaps(schedule.getStart(), schedule.getEnd(), rangeStart, rangeEnd)
				? List.of(occurrence(schedule, schedule.getStart(), schedule.getEnd(), null))
				: List.of();
		}

		return recurrenceStarts(schedule, rangeStart.minus(duration), rangeEnd).stream()
			.map(start -> occurrence(schedule, start, start.plus(duration), occurrenceId(schedule, start)))
			.filter(value -> overlaps(value.start(), value.end(), rangeStart, rangeEnd))
			.toList();
	}

	private List<LocalDateTime> recurrenceStarts(Schedule schedule, LocalDateTime searchStart, LocalDateTime searchEnd) {
		if (schedule.isAllDay()) {
			Recur<LocalDate> recurrence = recurrenceRuleValidator.parseAllDay(schedule.getRecurrenceRule());
			LocalDate seed = schedule.getStart().toLocalDate();
			LocalDate periodStart = searchStart.toLocalDate();
			LocalDate periodEnd = searchEnd.toLocalDate();
			return recurrence.getDates(seed, periodStart, periodEnd).stream()
				.map(LocalDate::atStartOfDay)
				.toList();
		}

		Recur<LocalDateTime> recurrence = recurrenceRuleValidator.parseTimed(schedule.getRecurrenceRule());
		Temporal until = recurrence.getUntil();
		if (!(until instanceof OffsetDateTime offsetDateTime)) {
			return recurrence.getDates(schedule.getStart(), searchStart, searchEnd);
		}

		LocalDateTime untilInScheduleZone = offsetDateTime.atZoneSameInstant(SCHEDULE_ZONE_ID).toLocalDateTime();
		Recur<LocalDateTime> recurrenceWithoutUntil = recurrenceRuleValidator.parseTimed(
			withoutUntil(schedule.getRecurrenceRule()));
		return recurrenceWithoutUntil.getDates(schedule.getStart(), searchStart, searchEnd).stream()
			.filter(start -> !start.isAfter(untilInScheduleZone))
			.toList();
	}

	private String withoutUntil(String recurrenceRule) {
		return Arrays.stream(recurrenceRule.split(";", -1))
			.filter(part -> !part.regionMatches(true, 0, "UNTIL=", 0, "UNTIL=".length()))
			.collect(Collectors.joining(";"));
	}

	private ScheduleOccurrence occurrence(Schedule schedule, LocalDateTime start, LocalDateTime end, String occurrenceId) {
		return new ScheduleOccurrence(schedule, start, end, occurrenceId);
	}

	private String occurrenceId(Schedule schedule, LocalDateTime start) {
		return schedule.getId() + "/" + start.atZone(SCHEDULE_ZONE_ID).format(OCCURRENCE_ID_FORMATTER);
	}

	private boolean overlaps(LocalDateTime start, LocalDateTime end, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
		return start.isBefore(rangeEnd) && end.isAfter(rangeStart);
	}
}
