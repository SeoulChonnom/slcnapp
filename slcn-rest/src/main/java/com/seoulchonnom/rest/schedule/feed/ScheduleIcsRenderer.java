package com.seoulchonnom.rest.schedule.feed;

import static com.seoulchonnom.spec.schedule.constant.ScheduleConstant.SCHEDULE_ZONE_ID;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.Temporal;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.TextList;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.TzId;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.LastModified;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Method;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Sequence;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;

import com.seoulchonnom.spec.schedule.entity.Schedule;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedEvent;

@Component
public class ScheduleIcsRenderer {
	private static final String PROD_ID = "-//SLCN//Schedule Feed//KO";
	private static final URI ORGANIZER_URI = URI.create("https://github.com/SeoulChonnom/slcnapp");
	private static final String TIMEZONE_UPDATE_PROPERTY = "net.fortuna.ical4j.timezone.update.enabled";
	private static final int RFC5545_FOLD_LENGTH = 75;

	static {
		disableTimeZoneUpdates();
	}

	public RenderedCalendar render(List<ScheduleFeedEvent> events) {
		Objects.requireNonNull(events, "events");
		disableTimeZoneUpdates();
		TimeZoneRegistry timeZoneRegistry = TimeZoneRegistryFactory.getInstance().createRegistry();
		net.fortuna.ical4j.model.TimeZone seoulTimeZone = timeZoneRegistry.getTimeZone(SCHEDULE_ZONE_ID.getId());
		if (seoulTimeZone == null) {
			throw new IllegalStateException("Asia/Seoul timezone is unavailable");
		}

		net.fortuna.ical4j.model.Calendar calendar = new net.fortuna.ical4j.model.Calendar();
		Version version = new Version();
		version.setMaxVersion(Version.VALUE_2_0);
		calendar.add(version);
		calendar.add(new ProdId(PROD_ID));
		calendar.add(new CalScale(CalScale.VALUE_GREGORIAN));
		calendar.add(new Method(Method.VALUE_PUBLISH));
		calendar.add(seoulTimeZone.getVTimeZone());

		events.stream()
			.sorted(eventComparator())
			.map(event -> toVEvent(event, timeZoneRegistry))
			.forEach(calendar::add);

		String body = output(calendar);
		return new RenderedCalendar(body, etag(body));
	}

	private Comparator<ScheduleFeedEvent> eventComparator() {
		return Comparator
			.comparing((ScheduleFeedEvent event) -> event.schedule().getStart(), Comparator.nullsFirst(Comparator.naturalOrder()))
			.thenComparing(event -> event.schedule().getId(), Comparator.nullsFirst(Comparator.naturalOrder()));
	}

	private VEvent toVEvent(ScheduleFeedEvent feedEvent, TimeZoneRegistry timeZoneRegistry) {
		Schedule schedule = feedEvent.schedule();
		validateDateRange(schedule);
		String uid = schedule.getId() + "@slcn";
		long modifiedTime = Math.max(schedule.getModifiedTime(), feedEvent.calendar().getModifiedTime());
		Instant modifiedInstant = Instant.ofEpochMilli(modifiedTime);

		VEvent event = new VEvent(false);
		event.add(new Uid(uid));
		event.add(new Organizer(ORGANIZER_URI));
		event.add(new DtStamp(modifiedInstant));
		event.add(new LastModified(modifiedInstant));
		event.add(new Sequence(Math.toIntExact(schedule.getEntityVersion())));
		if (schedule.isAllDay()) {
			addAllDayDates(event, schedule);
		} else {
			addTimedDates(event, schedule, timeZoneRegistry);
		}
		String calendarName = normalizeText(feedEvent.calendar().getName());
		event.add(new Summary("[" + calendarName + "] " + normalizeText(schedule.getTitle())));
		if (hasText(schedule.getBody())) {
			event.add(new Description(normalizeText(schedule.getBody())));
		}
		event.add(new Categories(new TextList(List.of(calendarName))));
		if (hasText(schedule.getLocation())) {
			event.add(new Location(normalizeText(schedule.getLocation())));
		}
		if (hasText(schedule.getRecurrenceRule())) {
			event.add(new RawRRuleProperty(schedule.getRecurrenceRule()));
		}
		return event;
	}

	private void validateDateRange(Schedule schedule) {
		if (schedule.getStart() == null || schedule.getEnd() == null
			|| !schedule.getStart().isBefore(schedule.getEnd())) {
			throw new IllegalStateException("Schedule start must be before end");
		}
	}

	private void addTimedDates(VEvent event, Schedule schedule, TimeZoneRegistry timeZoneRegistry) {
		DtStart<ZonedDateTime> start = new DtStart<>(schedule.getStart().atZone(SCHEDULE_ZONE_ID));
		DtEnd<ZonedDateTime> end = new DtEnd<>(schedule.getEnd().atZone(SCHEDULE_ZONE_ID));
		start.setTimeZoneRegistry(timeZoneRegistry);
		end.setTimeZoneRegistry(timeZoneRegistry);
		TzId timeZoneId = new TzId(SCHEDULE_ZONE_ID.getId());
		start.replace(timeZoneId);
		end.replace(new TzId(SCHEDULE_ZONE_ID.getId()));
		event.add(start);
		event.add(end);
	}

	private void addAllDayDates(VEvent event, Schedule schedule) {
		LocalDate startDate = schedule.getStart().toLocalDate();
		LocalDate endDate = schedule.getEnd().toLocalDate();
		event.add(new DtStart<>(startDate));
		event.add(new DtEnd<>(endDate));
	}

	private String output(net.fortuna.ical4j.model.Calendar calendar) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			new CalendarOutputter(false, Integer.MAX_VALUE).output(calendar, output);
		} catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("Unable to render schedule feed", exception);
		}
		return foldLines(output.toString(StandardCharsets.UTF_8));
	}

	private String foldLines(String body) {
		StringBuilder folded = new StringBuilder(body.length());
		String[] lines = body.split("\\r\\n", -1);
		int lineCount = body.endsWith("\r\n") ? lines.length - 1 : lines.length;
		for (int index = 0; index < lineCount; index++) {
			appendFoldedLine(folded, lines[index]);
		}
		return folded.toString();
	}

	private void appendFoldedLine(StringBuilder output, String line) {
		if (line.isEmpty()) {
			output.append("\r\n");
			return;
		}

		int offset = 0;
		int lineLimit = RFC5545_FOLD_LENGTH;
		boolean first = true;
		while (offset < line.length()) {
			int start = offset;
			int bytes = 0;
			while (offset < line.length()) {
				int codePoint = line.codePointAt(offset);
				int codePointBytes = utf8Length(codePoint);
				if (bytes + codePointBytes > lineLimit) {
					break;
				}
				bytes += codePointBytes;
				offset += Character.charCount(codePoint);
			}
			if (start == offset) {
				throw new IllegalStateException("Unable to fold RFC 5545 content line");
			}
			if (!first) {
				output.append(' ');
			}
			output.append(line, start, offset).append("\r\n");
			first = false;
			lineLimit = RFC5545_FOLD_LENGTH - 1;
		}
	}

	private int utf8Length(int codePoint) {
		if (codePoint <= 0x7f) {
			return 1;
		}
		if (codePoint <= 0x7ff) {
			return 2;
		}
		if (codePoint <= 0xffff) {
			return 3;
		}
		return 4;
	}

	private String etag(String body) {
		byte[] digest;
		try {
			digest = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
		return '"' + HexFormat.of().formatHex(digest) + '"';
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private String normalizeText(String value) {
		return value == null ? null : value.replace("\r\n", "\n").replace('\r', '\n');
	}

	private static void disableTimeZoneUpdates() {
		System.setProperty(TIMEZONE_UPDATE_PROPERTY, "false");
	}

	public record RenderedCalendar(String body, String etag) {
	}

	private static final class RawRRuleProperty extends RRule<Temporal> {
		private final String rawValue;

		private RawRRuleProperty(String value) {
			super(value);
			this.rawValue = value;
		}

		@Override
		public String toString() {
			return "RRULE:" + rawValue + "\r\n";
		}
	}
}
