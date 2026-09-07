package com.seoulchonnom.rest.schedule.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.TimeZoneUpdater;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.Categories;

import com.seoulchonnom.spec.calendar.entity.Calendar;
import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedContent;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedEvent;

class ScheduleIcsRendererTest {
	private static final String ORGANIZER_URI = "https://github.com/SeoulChonnom/slcnapp";
	private static final String TIMEZONE_UPDATE_PROPERTY = "net.fortuna.ical4j.timezone.update.enabled";

	private final ScheduleIcsRenderer renderer = new ScheduleIcsRenderer();

	@Test
	void render_shouldMapCalendarAndScheduleFieldsToCanonicalEvent() throws Exception {
		ScheduleFeedRendererFixture fixture = fixture(
			"SCHEDULE-0001",
			"데이트",
			"저녁 약속",
			"성수동 식당 예약",
			false,
			LocalDateTime.of(2026, 9, 3, 19, 0),
			LocalDateTime.of(2026, 9, 3, 20, 0),
			"성수동",
			null,
			4,
			1_757_000_000_000L,
			1_756_000_000_000L);

		ScheduleIcsRenderer.RenderedCalendar rendered = renderer.render(new ScheduleFeedContent("테스트 캘린더", List.of(fixture.event())));
		net.fortuna.ical4j.model.Calendar calendar = parse(rendered.body());
		VEvent event = calendar.getComponents(Component.VEVENT).stream()
			.map(VEvent.class::cast)
			.findFirst()
			.orElseThrow();

		assertThat(calendar.getPropertyList().getProperty(Property.VERSION).orElseThrow().getValue()).isEqualTo("2.0");
		assertThat(calendar.getPropertyList().getProperty(Property.PRODID).orElseThrow().getValue())
			.isEqualTo("-//SLCN//Schedule Feed//KO");
		assertThat(calendar.getPropertyList().getProperty(Property.CALSCALE).orElseThrow().getValue()).isEqualTo("GREGORIAN");
		assertThat(calendar.getPropertyList().getProperty(Property.METHOD).orElseThrow().getValue()).isEqualTo("PUBLISH");
		assertThat(rendered.body()).startsWith(
			"BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//SLCN//Schedule Feed//KO\r\n"
				+ "CALSCALE:GREGORIAN\r\nMETHOD:PUBLISH\r\n"
				+ "X-WR-CALNAME:테스트 캘린더\r\nX-WR-TIMEZONE:Asia/Seoul\r\nBEGIN:VTIMEZONE\r\n");
		assertThat(property(event, Property.UID)).isEqualTo("SCHEDULE-0001@slcn");
		assertThat(property(event, Property.SUMMARY)).isEqualTo("[데이트] 저녁 약속");
		assertThat(property(event, Property.DESCRIPTION)).isEqualTo("성수동 식당 예약");
		assertThat(property(event, Property.CATEGORIES)).isEqualTo("데이트");
		assertThat(property(event, Property.LOCATION)).isEqualTo("성수동");
		assertThat(property(event, Property.SEQUENCE)).isEqualTo("4");
		assertThat(property(event, Property.ORGANIZER)).isEqualTo(ORGANIZER_URI);
		assertThat(property(event, Property.DTSTAMP)).isEqualTo("20250904T153320Z");
		assertThat(property(event, Property.LAST_MODIFIED)).isEqualTo("20250904T153320Z");
		assertThat(calendar.validate().hasErrors()).isFalse();
		assertThat(rendered.etag()).matches("\"[0-9a-f]{64}\"");
		String expectedEtag = "\"" + HexFormat.of().formatHex(
			MessageDigest.getInstance("SHA-256").digest(rendered.body().getBytes(StandardCharsets.UTF_8))) + "\"";
		assertThat(rendered.etag()).isEqualTo(expectedEtag);
	}

	@Test
	void render_shouldEmitCalendarNameAndTimeZoneProperties() throws Exception {
		ScheduleFeedRendererFixture fixture = fixture(
			"SCHEDULE-0001", "데이트", "저녁 약속", null, false,
			LocalDateTime.of(2026, 9, 3, 19, 0),
			LocalDateTime.of(2026, 9, 3, 20, 0),
			null, null, 1, 1_757_000_000_000L, 1_756_000_000_000L);

		ScheduleIcsRenderer.RenderedCalendar rendered = renderer.render(
			new ScheduleFeedContent("가족 캘린더", List.of(fixture.event())));

		assertThat(rendered.body()).contains("X-WR-CALNAME:가족 캘린더");
		assertThat(rendered.body()).contains("X-WR-TIMEZONE:Asia/Seoul");
	}

	@Test
	void render_shouldProduceDifferentEtagForDifferentFeedName() {
		ScheduleFeedRendererFixture fixture = fixture(
			"SCHEDULE-0001", "데이트", "저녁 약속", null, false,
			LocalDateTime.of(2026, 9, 3, 19, 0),
			LocalDateTime.of(2026, 9, 3, 20, 0),
			null, null, 1, 1_757_000_000_000L, 1_756_000_000_000L);

		String first = renderer.render(new ScheduleFeedContent("가족", List.of(fixture.event()))).etag();
		String second = renderer.render(new ScheduleFeedContent("회사", List.of(fixture.event()))).etag();

		assertThat(first).isNotEqualTo(second);
	}

	@Test
	void renderer_shouldDisableIcal4jTimezoneUpdatesBeforeRegistryCreation() {
		assertThat(System.getProperty(TIMEZONE_UPDATE_PROPERTY)).isEqualTo("false");
		assertThat(new TimeZoneUpdater().isEnabled()).isFalse();
	}

	@Test
	void render_shouldUseSeoulTimezoneForTimedEventsAndIncludeOneTimezoneComponent() throws Exception {
		ScheduleFeedRendererFixture fixture = fixture(
			"SCHEDULE-0002",
			"업무",
			"회의",
			"",
			false,
			LocalDateTime.of(2026, 9, 3, 9, 30),
			LocalDateTime.of(2026, 9, 3, 10, 0),
			null,
			null,
			0,
			1_757_000_000_000L,
			1_757_000_000_000L);

		ScheduleIcsRenderer.RenderedCalendar rendered = renderer.render(new ScheduleFeedContent("테스트 캘린더", List.of(fixture.event())));
		net.fortuna.ical4j.model.Calendar calendar = parse(rendered.body());
		VEvent event = calendar.getComponents(Component.VEVENT).stream()
			.map(VEvent.class::cast)
			.findFirst()
			.orElseThrow();
		List<VTimeZone> timeZones = calendar.getComponents(Component.VTIMEZONE);

		assertThat(timeZones).hasSize(1);
		assertThat(timeZones.get(0).getPropertyList().getProperty(Property.TZID).orElseThrow().getValue())
			.isEqualTo("Asia/Seoul");
		assertThat(event.getStartDate().orElseThrow().getDate())
			.isInstanceOf(ZonedDateTime.class)
			.extracting(value -> ((ZonedDateTime)value).toLocalDateTime())
			.isEqualTo(LocalDateTime.of(2026, 9, 3, 9, 30));
		assertThat(event.getEndDate().orElseThrow().getDate())
			.isInstanceOf(ZonedDateTime.class)
			.extracting(value -> ((ZonedDateTime)value).toLocalDateTime())
			.isEqualTo(LocalDateTime.of(2026, 9, 3, 10, 0));
		assertThat(event.getStartDate().orElseThrow().getParameter(Property.TZID).orElseThrow().getValue())
			.isEqualTo("Asia/Seoul");
		assertThat(calendar.validate().hasErrors()).isFalse();
	}

	@Test
	void render_shouldUseDateValuesAndExclusiveEndForAllDayEvents() throws Exception {
		ScheduleFeedRendererFixture fixture = fixture(
			"SCHEDULE-0003",
			"휴일",
			"추석",
			null,
			true,
			LocalDate.of(2026, 9, 3).atStartOfDay(),
			LocalDate.of(2026, 9, 4).atStartOfDay(),
			null,
			null,
			1,
			1_757_000_000_000L,
			1_757_000_000_000L);

		net.fortuna.ical4j.model.Calendar calendar = parse(renderer.render(new ScheduleFeedContent("테스트 캘린더", List.of(fixture.event()))).body());
		VEvent event = calendar
			.getComponents(Component.VEVENT).stream()
			.map(VEvent.class::cast)
			.findFirst()
			.orElseThrow();

		assertThat(event.getStartDate().orElseThrow().getDate()).isEqualTo(LocalDate.of(2026, 9, 3));
		assertThat(event.getEndDate().orElseThrow().getDate()).isEqualTo(LocalDate.of(2026, 9, 4));
		assertThat(event.getStartDate().orElseThrow().getParameter("VALUE").orElseThrow().getValue()).isEqualTo("DATE");
		assertThat(event.getEndDate().orElseThrow().getParameter("VALUE").orElseThrow().getValue()).isEqualTo("DATE");
		assertThat(calendar.validate().hasErrors()).isFalse();
	}

	@Test
	void render_shouldRejectAllDayRangeThatCollapsesToSameDate() {
		LocalDateTime start = LocalDateTime.of(2026, 9, 3, 9, 0);
		ScheduleFeedRendererFixture fixture = fixture(
			"SCHEDULE-0011",
			"종일 일정",
			"같은 날짜",
			null,
			true,
			start,
			LocalDateTime.of(2026, 9, 3, 10, 0),
			null,
			null,
			0,
			1_757_000_000_000L,
			1_757_000_000_000L);

		assertThatThrownBy(() -> renderer.render(new ScheduleFeedContent("테스트 캘린더", List.of(fixture.event()))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Schedule start must be before end");
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void render_shouldRejectNonPositiveDurationInsteadOfEmittingInvalidDtEnd(boolean allDay) {
		LocalDateTime start = LocalDateTime.of(2026, 9, 3, 9, 0);
		ScheduleFeedRendererFixture fixture = fixture(
			"SCHEDULE-0010",
			"잘못된 일정",
			"동일 시각",
			null,
			allDay,
			start,
			start,
			null,
			null,
			0,
			1_757_000_000_000L,
			1_757_000_000_000L);

		assertThatThrownBy(() -> renderer.render(new ScheduleFeedContent("테스트 캘린더", List.of(fixture.event()))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Schedule start must be before end");
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void render_shouldRejectReversedDateRangeInsteadOfEmittingInvalidDtEnd(boolean allDay) {
		LocalDateTime start = allDay
			? LocalDateTime.of(2026, 9, 4, 0, 0)
			: LocalDateTime.of(2026, 9, 3, 10, 0);
		LocalDateTime end = allDay
			? LocalDateTime.of(2026, 9, 3, 0, 0)
			: LocalDateTime.of(2026, 9, 3, 9, 0);
		ScheduleFeedRendererFixture fixture = fixture(
			"SCHEDULE-0012",
			"잘못된 일정",
			"역순 시각",
			null,
			allDay,
			start,
			end,
			null,
			null,
			0,
			1_757_000_000_000L,
			1_757_000_000_000L);

		assertThatThrownBy(() -> renderer.render(new ScheduleFeedContent("테스트 캘린더", List.of(fixture.event()))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Schedule start must be before end");
	}

	@Test
	void render_shouldRejectNullStartInsteadOfEmittingIncompleteEvent() {
		ScheduleFeedRendererFixture fixture = fixture(
			"SCHEDULE-0013",
			"잘못된 일정",
			"시작 없음",
			null,
			false,
			null,
			LocalDateTime.of(2026, 9, 3, 10, 0),
			null,
			null,
			0,
			1_757_000_000_000L,
			1_757_000_000_000L);

		assertThatThrownBy(() -> renderer.render(new ScheduleFeedContent("테스트 캘린더", List.of(fixture.event()))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Schedule start must be before end");
	}

	@Test
	void render_shouldRejectNullEndInsteadOfEmittingIncompleteEvent() {
		ScheduleFeedRendererFixture fixture = fixture(
			"SCHEDULE-0014",
			"잘못된 일정",
			"종료 없음",
			null,
			false,
			LocalDateTime.of(2026, 9, 3, 9, 0),
			null,
			null,
			null,
			0,
			1_757_000_000_000L,
			1_757_000_000_000L);

		assertThatThrownBy(() -> renderer.render(new ScheduleFeedContent("테스트 캘린더", List.of(fixture.event()))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Schedule start must be before end");
	}

	@Test
	void render_shouldPreserveRawRecurrenceRuleAndOmitBlankDescription() throws Exception {
		ScheduleFeedRendererFixture fixture = fixture(
			"SCHEDULE-0004",
			"반복",
			"주간 회의",
			" ",
			false,
			LocalDateTime.of(2026, 9, 3, 9, 0),
			LocalDateTime.of(2026, 9, 3, 10, 0),
			null,
			"BYDAY=TU;FREQ=WEEKLY;COUNT=10",
			2,
			1_757_000_000_000L,
			1_757_000_000_000L);

		ScheduleIcsRenderer.RenderedCalendar rendered = renderer.render(new ScheduleFeedContent("테스트 캘린더", List.of(fixture.event())));
		net.fortuna.ical4j.model.Calendar calendar = parse(rendered.body());
		VEvent event = calendar.getComponents(Component.VEVENT).stream()
			.map(VEvent.class::cast)
			.findFirst()
			.orElseThrow();

		assertThat(calendar.getComponents(Component.VEVENT)).hasSize(1);
		assertThat(rendered.body()).contains("RRULE:BYDAY=TU;FREQ=WEEKLY;COUNT=10\r\n");
		assertThat(property(event, Property.RRULE)).contains("FREQ=WEEKLY", "COUNT=10", "BYDAY=TU");
		assertThat(event.getPropertyList().getProperty(Property.DESCRIPTION)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"FREQ=DAILY\r\nX-INJECTED:VALUE",
		"FREQ=DAILY\nX-INJECTED:VALUE",
		"FREQ=DAILY;UNKNOWN=VALUE",
		"FREQ=NOT_SUPPORTED"
	})
	void render_shouldRejectUnsafeOrInvalidLegacyRecurrenceRule(String recurrenceRule) {
		ScheduleFeedRendererFixture fixture = fixture(
			"SCHEDULE-0015",
			"반복",
			"오래된 일정",
			null,
			false,
			LocalDateTime.of(2026, 9, 3, 9, 0),
			LocalDateTime.of(2026, 9, 3, 10, 0),
			null,
			recurrenceRule,
			0,
			1_757_000_000_000L,
			1_757_000_000_000L);

		assertThatThrownBy(() -> renderer.render(new ScheduleFeedContent("테스트 캘린더", List.of(fixture.event()))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Invalid schedule recurrence rule");
	}

	@Test
	void render_shouldEscapeTextFoldUtf8WithoutSplittingCharactersAndUseCrLf() throws Exception {
		String longText = "쉼표,세미콜론;역슬래시\\줄\r\n바꿈-\r" + "한글".repeat(40);
		ScheduleFeedRendererFixture fixture = fixture(
			"SCHEDULE-0005",
			"긴 캘린더",
			longText,
			longText,
			false,
			LocalDateTime.of(2026, 9, 3, 9, 0),
			LocalDateTime.of(2026, 9, 3, 10, 0),
			longText,
			null,
			0,
			1_757_000_000_000L,
			1_757_000_000_000L);

		String body = renderer.render(new ScheduleFeedContent("테스트 캘린더", List.of(fixture.event()))).body();

		assertThat(body).endsWith("\r\n");
		assertThat(body.replace("\r\n", "")).doesNotContain("\r").doesNotContain("\n");
		assertThat(body).contains("\\,").contains("\\;").contains("\\\\").contains("\\n");
		for (String line : body.split("\\r\\n", -1)) {
			assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
			if (line.startsWith(" ")) {
				assertThat(line.substring(1)).isNotEmpty();
			}
		}
		VEvent parsed = parse(body).getComponents(Component.VEVENT).stream()
			.map(VEvent.class::cast)
			.findFirst()
			.orElseThrow();
		String normalizedText = longText.replace("\r\n", "\n").replace('\r', '\n');
		assertThat(property(parsed, Property.SUMMARY)).isEqualTo("[긴 캘린더] " + normalizedText);
		assertThat(property(parsed, Property.DESCRIPTION)).isEqualTo(normalizedText);
		assertThat(property(parsed, Property.LOCATION)).isEqualTo(normalizedText);
	}

	@Test
	void render_shouldNormalizeLoneCarriageReturnsInEveryTextProperty() throws Exception {
		String calendarName = "캘린더\r\n이름\r끝";
		String title = "제목\r\n다음\r끝";
		String body = "본문\r\n다음\r끝";
		String location = "장소\r\n다음\r끝";
		ScheduleIcsRenderer.RenderedCalendar rendered = renderer.render(new ScheduleFeedContent("테스트 캘린더", List.of(fixture(
			"SCHEDULE-0009",
			calendarName,
			title,
			body,
			false,
			LocalDateTime.of(2026, 9, 3, 9, 0),
			LocalDateTime.of(2026, 9, 3, 10, 0),
			location,
			null,
			0,
			1_757_000_000_000L,
			1_757_000_000_000L).event())));

		assertThat(rendered.body().replace("\r\n", "")).doesNotContain("\r");
		VEvent parsed = parse(rendered.body()).getComponents(Component.VEVENT).stream()
			.map(VEvent.class::cast)
			.findFirst()
			.orElseThrow();
		String normalizedCalendarName = "캘린더\n이름\n끝";
		assertThat(property(parsed, Property.SUMMARY)).isEqualTo("[" + normalizedCalendarName + "] 제목\n다음\n끝");
		assertThat(property(parsed, Property.DESCRIPTION)).isEqualTo("본문\n다음\n끝");
		assertThat(property(parsed, Property.LOCATION)).isEqualTo("장소\n다음\n끝");
		Categories categories = parsed.getPropertyList().<Categories>getProperty(Property.CATEGORIES).orElseThrow();
		assertThat(categories.getCategories().getTexts()).containsExactly(normalizedCalendarName);
	}

	@Test
	void render_shouldReturnValidEmptyCalendarWithStableEtag() throws Exception {
		ScheduleIcsRenderer.RenderedCalendar rendered = renderer.render(new ScheduleFeedContent("테스트 캘린더", List.of()));
		net.fortuna.ical4j.model.Calendar calendar = parse(rendered.body());

		assertThat(calendar.getComponents(Component.VEVENT)).isEmpty();
		assertThat(calendar.getComponents(Component.VTIMEZONE)).hasSize(1);
		assertThat(calendar.validate().hasErrors()).isFalse();
		String expectedEtag = "\"" + HexFormat.of().formatHex(
			MessageDigest.getInstance("SHA-256").digest(rendered.body().getBytes(StandardCharsets.UTF_8))) + "\"";
		assertThat(rendered.etag()).isEqualTo(expectedEtag);
	}

	@Test
	void render_shouldEscapeCalendarNameAsOneCategoryWhenItContainsTextDelimiters() throws Exception {
		String calendarName = "캘린더,세미콜론;역슬래시\\줄\n바꿈";
		ScheduleFeedRendererFixture fixture = fixture(
			"SCHEDULE-0008",
			calendarName,
			"일정",
			null,
			false,
			LocalDateTime.of(2026, 9, 3, 9, 0),
			LocalDateTime.of(2026, 9, 3, 10, 0),
			null,
			null,
			0,
			1_757_000_000_000L,
			1_757_000_000_000L);

		VEvent parsed = parse(renderer.render(new ScheduleFeedContent("테스트 캘린더", List.of(fixture.event()))).body()).getComponents(Component.VEVENT).stream()
			.map(VEvent.class::cast)
			.findFirst()
			.orElseThrow();

		assertThat(property(parsed, Property.SUMMARY)).isEqualTo("[" + calendarName + "] 일정");
		Categories categories = parsed.getPropertyList().<Categories>getProperty(Property.CATEGORIES).orElseThrow();
		assertThat(categories.getCategories().getTexts()).containsExactly(calendarName);
	}

	@Test
	void render_shouldBeByteDeterministicAndEtagShouldDependOnlyOnCanonicalBody() {
		ScheduleFeedRendererFixture first = fixture(
			"SCHEDULE-0006",
			"두 번째",
			"일정",
			null,
			false,
			LocalDateTime.of(2026, 9, 3, 11, 0),
			LocalDateTime.of(2026, 9, 3, 12, 0),
			null,
			null,
			0,
			1_757_000_000_000L,
			1_756_000_000_000L);
		ScheduleFeedRendererFixture second = fixture(
			"SCHEDULE-0007",
			"첫 번째",
			"일정",
			null,
			false,
			LocalDateTime.of(2026, 9, 3, 9, 0),
			LocalDateTime.of(2026, 9, 3, 10, 0),
			null,
			null,
			0,
			1_757_000_000_000L,
			1_756_000_000_000L);

		ScheduleIcsRenderer.RenderedCalendar firstRendered = renderer.render(
			new ScheduleFeedContent("테스트 캘린더", List.of(first.event(), second.event())));
		ScheduleIcsRenderer.RenderedCalendar reordered = renderer.render(
			new ScheduleFeedContent("테스트 캘린더", List.of(second.event(), first.event())));

		assertThat(reordered.body()).isEqualTo(firstRendered.body());
		assertThat(reordered.etag()).isEqualTo(firstRendered.etag());
	}

	private net.fortuna.ical4j.model.Calendar parse(String body) throws Exception {
		return new CalendarBuilder().build(new StringReader(body));
	}

	private String property(VEvent event, String name) {
		return event.getPropertyList().getProperty(name).orElseThrow().getValue();
	}

	private ScheduleFeedRendererFixture fixture(
		String scheduleId,
		String calendarName,
		String title,
		String body,
		boolean allDay,
		LocalDateTime start,
		LocalDateTime end,
		String location,
		String recurrenceRule,
		long entityVersion,
		long scheduleModifiedTime,
		long calendarModifiedTime
	) {
		Schedule schedule = Schedule.builder()
			.calendarId("CALENDAR-0001")
			.title(title)
			.body(body)
			.allDay(allDay)
			.start(start)
			.end(end)
			.location(location)
			.recurrenceRule(recurrenceRule)
			.build();
		schedule.setId(scheduleId);
		schedule.setEntityVersion(entityVersion);
		schedule.setModifiedTime(scheduleModifiedTime);

		Calendar calendar = Calendar.builder().name(calendarName).build();
		calendar.setId("CALENDAR-0001");
		calendar.setModifiedTime(calendarModifiedTime);
		return new ScheduleFeedRendererFixture(new ScheduleFeedEvent(schedule, calendar));
	}

	private record ScheduleFeedRendererFixture(ScheduleFeedEvent event) {
	}
}
