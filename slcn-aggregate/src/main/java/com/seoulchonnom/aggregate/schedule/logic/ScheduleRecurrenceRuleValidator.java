package com.seoulchonnom.aggregate.schedule.logic;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.validate.ValidationResult;

import com.seoulchonnom.aggregate.schedule.exception.InvalidScheduleRecurrenceException;

@Component
public class ScheduleRecurrenceRuleValidator {
	private static final Set<String> SUPPORTED_KEYS = Set.of(
		"FREQ", "COUNT", "UNTIL", "BYDAY", "BYMONTHDAY"
	);
	private static final Set<String> SUPPORTED_FREQUENCIES = Set.of(
		"DAILY", "WEEKLY", "MONTHLY", "YEARLY"
	);
	private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z]+");
	private static final Pattern BYDAY_TOKEN_PATTERN = Pattern.compile(
		"(?i)(?:[+-]?\\d{1,2})?(?:MO|TU|WE|TH|FR|SA|SU)"
	);
	private static final Pattern BYMONTHDAY_TOKEN_PATTERN = Pattern.compile("[+-]?\\d{1,2}");
	private static final Pattern DATE_PATTERN = Pattern.compile("\\d{8}");
	private static final Pattern UTC_DATE_TIME_PATTERN = Pattern.compile("\\d{8}T\\d{6}Z");
	private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder()
		.appendPattern("uuuuMMdd")
		.toFormatter(Locale.ROOT)
		.withResolverStyle(ResolverStyle.STRICT);
	private static final DateTimeFormatter UTC_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
		.appendPattern("uuuuMMdd'T'HHmmss'Z'")
		.toFormatter(Locale.ROOT)
		.withResolverStyle(ResolverStyle.STRICT);

	public String validateAndNormalize(String recurrenceRule, boolean allDay) {
		parseValidated(recurrenceRule, allDay);
		return StringUtils.hasText(recurrenceRule) ? recurrenceRule : null;
	}

	public Recur<LocalDateTime> parseTimed(String recurrenceRule) {
		RRule<LocalDateTime> rule = parseValidated(recurrenceRule, false);
		return rule == null ? null : rule.getRecur();
	}

	public Recur<LocalDate> parseAllDay(String recurrenceRule) {
		RRule<LocalDate> rule = parseValidated(recurrenceRule, true);
		return rule == null ? null : rule.getRecur();
	}

	private <T extends Temporal> RRule<T> parseValidated(String recurrenceRule, boolean allDay) {
		if (!StringUtils.hasText(recurrenceRule)) {
			return null;
		}

		Map<String, String> parts = parseParts(recurrenceRule);
		validatePartValues(parts, allDay);

		try {
			RRule<T> rule = new RRule<>(recurrenceRule);
			ValidationResult validationResult = rule.validate();
			if (validationResult.hasErrors()) {
				throw new InvalidScheduleRecurrenceException();
			}

			Recur<T> recur = rule.getRecur();
			if (!SUPPORTED_FREQUENCIES.contains(recur.getFrequency().name())) {
				throw new InvalidScheduleRecurrenceException();
			}
			validateUntilType(recur, allDay);
			return rule;
		} catch (InvalidScheduleRecurrenceException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new InvalidScheduleRecurrenceException();
		}
	}

	private Map<String, String> parseParts(String recurrenceRule) {
		Map<String, String> parts = new HashMap<>();
		String[] rawParts = recurrenceRule.split(";", -1);
		for (String rawPart : rawParts) {
			int separator = rawPart.indexOf('=');
			if (separator <= 0 || separator == rawPart.length() - 1 || separator != rawPart.lastIndexOf('=')) {
				throw new InvalidScheduleRecurrenceException();
			}

			String key = rawPart.substring(0, separator);
			if (!KEY_PATTERN.matcher(key).matches()) {
				throw new InvalidScheduleRecurrenceException();
			}
			String normalizedKey = key.toUpperCase(Locale.ROOT);
			if (!SUPPORTED_KEYS.contains(normalizedKey) || parts.put(normalizedKey, rawPart.substring(separator + 1)) != null) {
				throw new InvalidScheduleRecurrenceException();
			}
		}

		if (!parts.containsKey("FREQ")) {
			throw new InvalidScheduleRecurrenceException();
		}
		if (parts.containsKey("COUNT") && parts.containsKey("UNTIL")) {
			throw new InvalidScheduleRecurrenceException();
		}
		return parts;
	}

	private void validatePartValues(Map<String, String> parts, boolean allDay) {
		String frequency = parts.get("FREQ").toUpperCase(Locale.ROOT);
		if (!SUPPORTED_FREQUENCIES.contains(frequency)) {
			throw new InvalidScheduleRecurrenceException();
		}

		if (parts.containsKey("COUNT")) {
			validateCount(parts.get("COUNT"));
		}
		if (parts.containsKey("UNTIL")) {
			validateUntil(parts.get("UNTIL"), allDay);
		}
		if (parts.containsKey("BYDAY")) {
			validateByDay(parts.get("BYDAY"), frequency);
		}
		if (parts.containsKey("BYMONTHDAY")) {
			validateByMonthDay(parts.get("BYMONTHDAY"));
		}
	}

	private void validateCount(String count) {
		if (!count.matches("[0-9]+")) {
			throw new InvalidScheduleRecurrenceException();
		}
		try {
			if (Integer.parseInt(count) <= 0) {
				throw new InvalidScheduleRecurrenceException();
			}
		} catch (NumberFormatException exception) {
			throw new InvalidScheduleRecurrenceException();
		}
	}

	private void validateUntil(String until, boolean allDay) {
		try {
			if (allDay) {
				if (!DATE_PATTERN.matcher(until).matches()) {
					throw new InvalidScheduleRecurrenceException();
				}
				LocalDate.parse(until, DATE_FORMATTER);
				return;
			}

			if (!UTC_DATE_TIME_PATTERN.matcher(until).matches()) {
				throw new InvalidScheduleRecurrenceException();
			}
			LocalDateTime.parse(until, UTC_DATE_TIME_FORMATTER);
		} catch (InvalidScheduleRecurrenceException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new InvalidScheduleRecurrenceException();
		}
	}

	private void validateByDay(String byDay, String frequency) {
		Arrays.stream(byDay.split(",", -1))
			.forEach(token -> validateByDayToken(token, frequency));
	}

	private void validateByDayToken(String token, String frequency) {
		if (!BYDAY_TOKEN_PATTERN.matcher(token).matches()) {
			throw new InvalidScheduleRecurrenceException();
		}
		String ordinal = token.substring(0, Math.max(0, token.length() - 2));
		if (ordinal.isEmpty()) {
			return;
		}
		if (frequency.equals("DAILY") || frequency.equals("WEEKLY")) {
			throw new InvalidScheduleRecurrenceException();
		}
		try {
			int value = Integer.parseInt(ordinal);
			if (value == 0 || Math.abs(value) > 53) {
				throw new InvalidScheduleRecurrenceException();
			}
		} catch (NumberFormatException exception) {
			throw new InvalidScheduleRecurrenceException();
		}
	}

	private void validateByMonthDay(String byMonthDay) {
		Arrays.stream(byMonthDay.split(",", -1))
			.forEach(this::validateByMonthDayToken);
	}

	private void validateByMonthDayToken(String token) {
		if (!BYMONTHDAY_TOKEN_PATTERN.matcher(token).matches()) {
			throw new InvalidScheduleRecurrenceException();
		}
		try {
			int value = Integer.parseInt(token);
			if (value == 0 || Math.abs(value) > 31) {
				throw new InvalidScheduleRecurrenceException();
			}
		} catch (NumberFormatException exception) {
			throw new InvalidScheduleRecurrenceException();
		}
	}

	private void validateUntilType(Recur<?> recur, boolean allDay) {
		Temporal until = recur.getUntil();
		if (until == null) {
			return;
		}
		if (allDay && !(until instanceof LocalDate)) {
			throw new InvalidScheduleRecurrenceException();
		}
		if (!allDay && !(until instanceof OffsetDateTime)
			|| (!allDay && !OffsetDateTime.from(until).getOffset().equals(ZoneOffset.UTC))) {
			throw new InvalidScheduleRecurrenceException();
		}
	}
}
