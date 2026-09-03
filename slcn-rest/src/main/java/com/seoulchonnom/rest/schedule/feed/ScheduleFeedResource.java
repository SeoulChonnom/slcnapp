package com.seoulchonnom.rest.schedule.feed;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.seoulchonnom.aggregate.schedule.feed.logic.ScheduleFeedTokenLogic;
import com.seoulchonnom.spec.schedule.feed.facade.ScheduleFeedFacade;
import com.seoulchonnom.spec.schedule.feed.facade.sdo.ScheduleFeedCdo;
import com.seoulchonnom.spec.schedule.feed.facade.sdo.ScheduleFeedCreatedRdo;
import com.seoulchonnom.spec.schedule.feed.facade.sdo.ScheduleFeedRdo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/schedule/feeds")
@RequiredArgsConstructor
public class ScheduleFeedResource implements ScheduleFeedFacade {
	private final ScheduleFeedTokenLogic scheduleFeedTokenLogic;

	@Override
	@PostMapping
	public ResponseEntity<ScheduleFeedCreatedRdo> createFeed(@RequestBody @Valid ScheduleFeedCdo scheduleFeedCdo) {
		ScheduleFeedTokenLogic.CreatedFeedToken created = scheduleFeedTokenLogic.create(scheduleFeedCdo.getName());
		String feedUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
			.path("/schedule/feeds/{feedToken}/calendar.ics")
			.buildAndExpand(created.rawToken())
			.toUriString();

		return new ResponseEntity<>(
			ScheduleFeedCreatedRdo.from(created.feedToken(), feedUrl),
			HttpStatus.CREATED);
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
}
