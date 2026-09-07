package com.seoulchonnom.rest.schedule.feed;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.seoulchonnom.aggregate.schedule.feed.flow.ScheduleFeedFlow;
import com.seoulchonnom.aggregate.schedule.feed.logic.ScheduleFeedTokenLogic;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedEvent;
import com.seoulchonnom.spec.schedule.feed.facade.ScheduleFeedFacade;
import com.seoulchonnom.spec.schedule.feed.facade.sdo.ScheduleFeedCdo;
import com.seoulchonnom.spec.schedule.feed.facade.sdo.ScheduleFeedCreatedRdo;
import com.seoulchonnom.spec.schedule.feed.facade.sdo.ScheduleFeedRdo;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/schedule/feeds")
@RequiredArgsConstructor
public class ScheduleFeedResource implements ScheduleFeedFacade {
	private final ScheduleFeedTokenLogic scheduleFeedTokenLogic;
	private final ScheduleFeedFlow scheduleFeedFlow;
	private final ScheduleIcsRenderer scheduleIcsRenderer;

	@Override
	@PostMapping
	public ResponseEntity<ScheduleFeedCreatedRdo> createFeed(@RequestBody @Valid ScheduleFeedCdo scheduleFeedCdo) {
		ScheduleFeedTokenLogic.CreatedFeedToken created = scheduleFeedTokenLogic.create(scheduleFeedCdo.getName());
		String feedUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
			.path("/schedule/feeds/{feedToken}/calendar.ics")
			.buildAndExpand(created.rawToken())
			.toUriString();

		return ResponseEntity.status(HttpStatus.CREATED)
			.cacheControl(CacheControl.noStore())
			.body(ScheduleFeedCreatedRdo.from(created.feedToken(), feedUrl));
	}

	@Override
	@GetMapping
	public ResponseEntity<List<ScheduleFeedRdo>> getFeeds() {
		List<ScheduleFeedRdo> feeds = scheduleFeedTokenLogic.getAll().stream()
			.map(ScheduleFeedRdo::from)
			.toList();
		return new ResponseEntity<>(feeds, HttpStatus.OK);
	}

	@Override
	@DeleteMapping("/{feedId}")
	public ResponseEntity<Void> deleteFeed(@PathVariable("feedId") String feedId) {
		scheduleFeedTokenLogic.delete(feedId);
		return ResponseEntity.noContent().build();
	}

	@Override
	@GetMapping(value = "/{feedToken}/calendar.ics", produces = "text/calendar; charset=UTF-8")
	@SecurityRequirements
	public ResponseEntity<String> getCalendar(
		@PathVariable("feedToken") String feedToken,
		@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
		List<ScheduleFeedEvent> events = scheduleFeedFlow.getFeedEvents(feedToken);
		ScheduleIcsRenderer.RenderedCalendar rendered = scheduleIcsRenderer.render(events);
		if (etagMatches(ifNoneMatch, rendered.etag())) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
				.eTag(unquote(rendered.etag()))
				.cacheControl(feedCacheControl())
				.build();
		}

		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
			.eTag(unquote(rendered.etag()))
			.cacheControl(feedCacheControl())
			.body(rendered.body());
	}

	private boolean etagMatches(String ifNoneMatch, String etag) {
		if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
			return false;
		}

		for (String candidate : ifNoneMatch.split(",")) {
			String trimmed = candidate.trim();
			if ("*".equals(trimmed)) {
				return true;
			}
			if (trimmed.startsWith("W/")) {
				trimmed = trimmed.substring(2).trim();
			}
			if (etag.equals(trimmed)) {
				return true;
			}
		}

		return false;
	}

	private String unquote(String etag) {
		if (etag != null && etag.length() >= 2 && etag.startsWith("\"") && etag.endsWith("\"")) {
			return etag.substring(1, etag.length() - 1);
		}
		return etag;
	}

	private CacheControl feedCacheControl() {
		return CacheControl.noCache().cachePrivate();
	}
}
