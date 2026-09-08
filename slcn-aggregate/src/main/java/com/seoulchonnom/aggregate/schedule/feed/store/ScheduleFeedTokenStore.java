package com.seoulchonnom.aggregate.schedule.feed.store;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.seoulchonnom.aggregate.schedule.feed.exception.ScheduleFeedNotFoundException;
import com.seoulchonnom.aggregate.schedule.feed.store.jpo.ScheduleFeedTokenJpo;
import com.seoulchonnom.aggregate.schedule.feed.store.mapper.ScheduleFeedTokenJpoMapper;
import com.seoulchonnom.aggregate.schedule.feed.store.repository.ScheduleFeedTokenRepository;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedToken;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleFeedTokenStore {
	private final ScheduleFeedTokenRepository scheduleFeedTokenRepository;
	private final ScheduleFeedTokenJpoMapper scheduleFeedTokenJpoMapper;

	@Transactional
	public ScheduleFeedToken save(ScheduleFeedToken token) {
		ScheduleFeedTokenJpo saved = scheduleFeedTokenRepository.save(scheduleFeedTokenJpoMapper.toJpo(token));
		return scheduleFeedTokenJpoMapper.toDomain(saved);
	}

	public List<ScheduleFeedToken> findAll() {
		return scheduleFeedTokenRepository.findAll().stream()
			.map(scheduleFeedTokenJpoMapper::toDomain)
			.sorted(Comparator.comparingLong(ScheduleFeedToken::getRegisteredTime)
				.thenComparing(ScheduleFeedToken::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
			.toList();
	}

	public Optional<ScheduleFeedToken> findByTokenHash(String tokenHash) {
		return scheduleFeedTokenRepository.findByTokenHash(tokenHash)
			.map(scheduleFeedTokenJpoMapper::toDomain);
	}

	@Transactional
	public void deleteById(String id) {
		if (id == null || id.isBlank()) {
			throw new ScheduleFeedNotFoundException();
		}
		scheduleFeedTokenRepository.findById(id).orElseThrow(ScheduleFeedNotFoundException::new);
		scheduleFeedTokenRepository.deleteById(id);
	}
}
