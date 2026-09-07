package com.seoulchonnom.aggregate.schedule.feed.logic;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.schedule.feed.exception.ScheduleFeedNotFoundException;
import com.seoulchonnom.aggregate.schedule.feed.store.ScheduleFeedTokenStore;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedToken;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleFeedTokenLogic {
	private final ScheduleFeedTokenStore scheduleFeedTokenStore;
	private final ScheduleFeedTokenHasher scheduleFeedTokenHasher;
	private final ScheduleFeedTokenGenerator scheduleFeedTokenGenerator;

	@Transactional
	public CreatedFeedToken create(String name) {
		validateName(name);

		String rawToken = scheduleFeedTokenGenerator.generate();
		ScheduleFeedToken token = ScheduleFeedToken.builder()
			.name(name)
			.tokenHash(scheduleFeedTokenHasher.hash(rawToken))
			.build();
		ScheduleFeedToken savedToken = scheduleFeedTokenStore.save(token);
		return new CreatedFeedToken(savedToken, rawToken);
	}

	public List<ScheduleFeedToken> getAll() {
		return scheduleFeedTokenStore.findAll();
	}

	@Transactional
	public void delete(String feedId) {
		if (feedId == null || feedId.isBlank()) {
			throw new ScheduleFeedNotFoundException();
		}
		scheduleFeedTokenStore.deleteById(feedId);
	}

	public ScheduleFeedToken validate(String rawToken) {
		if (rawToken == null || rawToken.isBlank() || containsWhitespace(rawToken)) {
			throw new ScheduleFeedNotFoundException();
		}

		String tokenHash;
		try {
			tokenHash = scheduleFeedTokenHasher.hash(rawToken);
		} catch (IllegalArgumentException exception) {
			throw new ScheduleFeedNotFoundException();
		}
		return scheduleFeedTokenStore.findByTokenHash(tokenHash)
			.orElseThrow(ScheduleFeedNotFoundException::new);
	}

	private void validateName(String name) {
		if (name == null || name.isBlank()) {
			throw new BadRequestException("name은 필수입니다.");
		}
		if (name.length() > 100) {
			throw new BadRequestException("name은 100자 이하여야 합니다.");
		}
	}

	private boolean containsWhitespace(String value) {
		return value.chars().anyMatch(character -> Character.isWhitespace(character) || Character.isISOControl(character));
	}

	public record CreatedFeedToken(ScheduleFeedToken feedToken, String rawToken) {
		@Override
		public String toString() {
			return "CreatedFeedToken[feedToken=" + feedToken + ", rawToken=<redacted>]";
		}
	}
}
