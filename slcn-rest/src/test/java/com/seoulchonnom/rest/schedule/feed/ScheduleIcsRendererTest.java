package com.seoulchonnom.rest.schedule.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.Categories;

import com.seoulchonnom.spec.calendar.entity.Calendar;
import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedEvent;

class ScheduleIcsRendererTest {
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

		ScheduleIcsRenderer.RenderedCalendar rendered = renderer.render(List.of(fixture.event()));
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
				+ "CALSCALE:GREGORIAN\r\nMETHOD:PUBLISH\r\nBEGIN:VTIMEZONE\r\n");
		assertThat(property(event, Property.UID)).isEqualTo("SCHEDULE-0001@slcn");
		assertThat(property(event, Property.SUMMARY)).isEqualTo("[데이트] 저녁 약속");
		assertThat(property(event, Property.DESCRIPTION)).isEqualTo("성수동 식당 예약");
		assertThat(property(event, Property.CATEGORIES)).isEqualTo("데이트");
		assertThat(property(event, Property.LOCATION)).isEqualTo("성수동");
		assertThat(property(event, Property.SEQUENCE)).isEqualTo("4");
		assertThat(rendered.etag()).matches("\"[0-9a-f]{64}\"");
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

		ScheduleIcsRenderer.RenderedCalendar rendered = renderer.render(List.of(fixture.event()));
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

		VEvent event = parse(renderer.render(List.of(fixture.event())).body())
			.getComponents(Component.VEVENT).stream()
			.map(VEvent.class::cast)
			.findFirst()
			.orElseThrow();

		assertThat(event.getStartDate().orElseThrow().getDate()).isEqualTo(LocalDate.of(2026, 9, 3));
		assertThat(event.getEndDate().orElseThrow().getDate()).isEqualTo(LocalDate.of(2026, 9, 4));
		assertThat(event.getStartDate().orElseThrow().getParameter("VALUE").orElseThrow().getValue()).isEqualTo("DATE");
		assertThat(event.getEndDate().orElseThrow().getParameter("VALUE").orElseThrow().getValue()).isEqualTo("DATE");
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

		ScheduleIcsRenderer.RenderedCalendar rendered = renderer.render(List.of(fixture.event()));
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

	@Test
	void render_shouldEscapeTextFoldUtf8WithoutSplittingCharactersAndUseCrLf() throws Exception {
		String longText = "쉼표,세미콜론;역슬래시\\줄\n바꿈-" + "한글".repeat(40);
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

		String body = renderer.render(List.of(fixture.event())).body();

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
		assertThat(property(parsed, Property.SUMMARY)).isEqualTo("[긴 캘린더] " + longText);
		assertThat(property(parsed, Property.DESCRIPTION)).isEqualTo(longText);
		assertThat(property(parsed, Property.LOCATION)).isEqualTo(longText);
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

		VEvent parsed = parse(renderer.render(List.of(fixture.event())).body()).getComponents(Component.VEVENT).stream()
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

		ScheduleIcsRenderer.RenderedCalendar firstRendered = renderer.render(List.of(first.event(), second.event()));
		ScheduleIcsRenderer.RenderedCalendar reordered = renderer.render(List.of(second.event(), first.event()));

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
			.hidden(false)
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
