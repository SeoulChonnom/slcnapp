package com.seoulchonnom.aggregate.schedule.logic;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import net.fortuna.ical4j.model.Recur;

import com.seoulchonnom.aggregate.schedule.exception.InvalidScheduleRecurrenceException;
import com.seoulchonnom.spec.common.exception.ErrorCode;

class ScheduleRecurrenceRuleValidatorTest {
	private final ScheduleRecurrenceRuleValidator validator = new ScheduleRecurrenceRuleValidator();

	@ParameterizedTest
	@ValueSource(strings = {
		"FREQ=DAILY;COUNT=3",
		"FREQ=WEEKLY;BYDAY=MO,WE,FR",
		"FREQ=MONTHLY;BYMONTHDAY=1;UNTIL=20261231T145959Z",
		"FREQ=DAILY;BYMONTHDAY=+1",
		"FREQ=YEARLY"
	})
	void validate_shouldAcceptSupportedRules(String rule) {
		assertThat(validator.validateAndNormalize(rule, false)).isEqualTo(rule);
	}

	@Test
	void validate_shouldPreserveValidRuleByteForByte() {
		String rule = "BYDAY=MO,WE;FREQ=WEEKLY;COUNT=3";

		assertThat(validator.validateAndNormalize(rule, false)).isEqualTo(rule);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", " ", "\t\n"})
	void validate_shouldNormalizeBlankRuleToNull(String rule) {
		assertThat(validator.validateAndNormalize(rule, false)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"FREQ=HOURLY",
		"FREQ=DAILY;EXDATE=20261231",
		"FREQ=DAILY;RDATE=20261231",
		"FREQ=DAILY;INTERVAL=2",
		"FREQ=DAILY;BYSECOND=30",
		"RRULE:FREQ=DAILY"
	})
	void validate_shouldRejectUnsupportedParts(String rule) {
		assertInvalid(rule, false);
	}

	@ParameterizedTest
	@MethodSource("malformedRules")
	void validate_shouldRejectMalformedValues(String rule, boolean allDay) {
		assertInvalid(rule, allDay);
	}

	private static Stream<Arguments> malformedRules() {
		return Stream.of(
			Arguments.of("FREQ=DAILY;COUNT=abc", false),
			Arguments.of("FREQ=DAILY;COUNT=0", false),
			Arguments.of("FREQ=DAILY;BYDAY=ZZ", false),
			Arguments.of("FREQ=DAILY;BYMONTHDAY=nope", false),
			Arguments.of("FREQ=DAILY;COUNT=2;UNTIL=20261231T145959Z", false),
			Arguments.of("FREQ=DAILY;UNTIL=20261231T145959Z", true),
			Arguments.of("FREQ=DAILY;UNTIL=20261231", false));
	}

	@Test
	void validate_shouldAcceptDateUntilForAllDayRule() {
		String rule = "FREQ=DAILY;UNTIL=20261231";

		assertThat(validator.validateAndNormalize(rule, true)).isEqualTo(rule);
	}

	@Test
	void validate_shouldAcceptUtcDateTimeUntilForTimedRule() {
		String rule = "FREQ=DAILY;UNTIL=20261231T145959Z";

		assertThat(validator.validateAndNormalize(rule, false)).isEqualTo(rule);
	}

	@Test
	void parseTimed_shouldReturnTypedRecurrence() {
		Recur<LocalDateTime> recurrence = validator.parseTimed("FREQ=DAILY;UNTIL=20261231T145959Z");

		assertThat(recurrence).isNotNull();
		assertThat(recurrence.getFrequency().name()).isEqualTo("DAILY");
	}

	@Test
	void parseAllDay_shouldReturnTypedRecurrence() {
		Recur<LocalDate> recurrence = validator.parseAllDay("FREQ=DAILY;UNTIL=20261231");

		assertThat(recurrence).isNotNull();
		assertThat(recurrence.getFrequency().name()).isEqualTo("DAILY");
	}

	private void assertInvalid(String rule, boolean allDay) {
		assertThatThrownBy(() -> validator.validateAndNormalize(rule, allDay))
			.isInstanceOf(InvalidScheduleRecurrenceException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_SCHEDULE_RECURRENCE);
	}
}
