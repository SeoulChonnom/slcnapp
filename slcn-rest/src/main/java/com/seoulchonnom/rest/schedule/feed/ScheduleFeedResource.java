package com.seoulchonnom.rest.schedule.feed;

import java.net.URI;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.util.UriComponentsBuilder;

import com.seoulchonnom.aggregate.schedule.feed.flow.ScheduleFeedFlow;
import com.seoulchonnom.aggregate.schedule.feed.logic.ScheduleFeedTokenLogic;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedContent;
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

	@Value("${slcn.public-base-url:}")
	private String publicBaseUrl;

	/** UserResource#contextPath와 같은 방식. SLCN_PUBLIC_BASE_URL 조립 시 중복 없이 붙인다. */
	@Value("${server.servlet.context-path:}")
	private String contextPath;

	@Override
	@PostMapping
	public ResponseEntity<ScheduleFeedCreatedRdo> createFeed(@RequestBody @Valid ScheduleFeedCdo scheduleFeedCdo) {
		ScheduleFeedTokenLogic.CreatedFeedToken created = scheduleFeedTokenLogic.create(scheduleFeedCdo.getName());

		return ResponseEntity.status(HttpStatus.CREATED)
			.cacheControl(CacheControl.noStore())
			.body(ScheduleFeedCreatedRdo.from(created.feedToken(), feedUrl(created.rawToken())));
	}

	private String feedUrl(String rawToken) {
		UriComponentsBuilder builder = publicBaseUrl == null || publicBaseUrl.isBlank()
			? ServletUriComponentsBuilder.fromCurrentContextPath()
			: configuredBaseUrlBuilder(publicBaseUrl);

		return builder
			.path("/schedule/feeds/{feedToken}/calendar.ics")
			.buildAndExpand(rawToken)
			.toUriString();
	}

	/**
	 * SLCN_PUBLIC_BASE_URL 로 조립하는 branch. fromCurrentContextPath() 와 같은 모양(context
	 * path 포함)이 되도록 server.servlet.context-path 를 붙인다. 이미 base URL에 context
	 * path가 포함돼 있으면(경로가 그 값으로 끝나면) 중복 부착하지 않는다. scheme/host가 없는
	 * 값(상대 URL)은 구독 불가능한 URL을 조용히 만들지 않고 여기서 바로 실패시킨다.
	 */
	private UriComponentsBuilder configuredBaseUrlBuilder(String rawBaseUrl) {
		String trimmed = rawBaseUrl.strip().replaceAll("/+$", "");
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(trimmed);
		URI baseUri = builder.build().toUri();
		if (baseUri.getScheme() == null || baseUri.getHost() == null) {
			throw new IllegalStateException(
				"slcn.public-base-url(SLCN_PUBLIC_BASE_URL)은 scheme과 host를 포함한 절대 URL이어야 합니다: "
					+ rawBaseUrl);
		}

		String normalizedContextPath = normalizeContextPath();
		String basePath = baseUri.getPath() == null ? "" : baseUri.getPath();
		if (!normalizedContextPath.isEmpty() && !basePath.endsWith(normalizedContextPath)) {
			builder.path(normalizedContextPath);
		}
		return builder;
	}

	private String normalizeContextPath() {
		if (contextPath == null || contextPath.isBlank()) {
			return "";
		}
		return contextPath.startsWith("/") ? contextPath : "/" + contextPath;
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
		ScheduleFeedContent content = scheduleFeedFlow.getFeedContent(feedToken);
		ScheduleIcsRenderer.RenderedCalendar rendered = scheduleIcsRenderer.render(content);
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
