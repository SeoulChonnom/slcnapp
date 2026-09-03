package com.seoulchonnom.aggregate.schedule.feed.store;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.aggregate.schedule.feed.exception.ScheduleFeedNotFoundException;
import com.seoulchonnom.aggregate.schedule.feed.store.jpo.ScheduleFeedTokenJpo;
import com.seoulchonnom.aggregate.schedule.feed.store.mapper.ScheduleFeedTokenJpoMapper;
import com.seoulchonnom.aggregate.schedule.feed.store.repository.ScheduleFeedTokenRepository;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedToken;

class ScheduleFeedTokenStoreTest {
	private final ScheduleFeedTokenRepository scheduleFeedTokenRepository = mock(ScheduleFeedTokenRepository.class);
	private final ScheduleFeedTokenJpoMapper scheduleFeedTokenJpoMapper = mock(ScheduleFeedTokenJpoMapper.class);
	private final ScheduleFeedTokenStore scheduleFeedTokenStore =
		new ScheduleFeedTokenStore(scheduleFeedTokenRepository, scheduleFeedTokenJpoMapper);

	@Test
	void findByTokenHash_shouldMapFoundToken() {
		ScheduleFeedTokenJpo scheduleFeedTokenJpo = scheduleFeedTokenJpo("FEED-0001", 100L, "Google Calendar", "sha256-hex");
		ScheduleFeedToken token = token("FEED-0001", 100L, "Google Calendar", "sha256-hex");
		when(scheduleFeedTokenRepository.findByTokenHash("sha256-hex"))
			.thenReturn(Optional.of(scheduleFeedTokenJpo));
		when(scheduleFeedTokenJpoMapper.toDomain(scheduleFeedTokenJpo)).thenReturn(token);

		Optional<ScheduleFeedToken> result = scheduleFeedTokenStore.findByTokenHash("sha256-hex");

		assertThat(result).containsSame(token);
		verify(scheduleFeedTokenRepository).findByTokenHash("sha256-hex");
		verify(scheduleFeedTokenJpoMapper).toDomain(scheduleFeedTokenJpo);
	}

	@Test
	void findByTokenHash_shouldReturnEmptyWhenHashIsUnknown() {
		when(scheduleFeedTokenRepository.findByTokenHash("unknown-hash")).thenReturn(Optional.empty());

		assertThat(scheduleFeedTokenStore.findByTokenHash("unknown-hash")).isEmpty();
		verify(scheduleFeedTokenRepository).findByTokenHash("unknown-hash");
		verifyNoInteractions(scheduleFeedTokenJpoMapper);
	}

	@Test
	void findAll_shouldSortByRegisteredTimeThenId() {
		ScheduleFeedTokenJpo later = scheduleFeedTokenJpo("FEED-0002", 200L, "Later", "hash-2");
		ScheduleFeedTokenJpo sameTimeHigherId = scheduleFeedTokenJpo("FEED-0003", 100L, "Same time", "hash-3");
		ScheduleFeedTokenJpo first = scheduleFeedTokenJpo("FEED-0001", 100L, "First", "hash-1");
		when(scheduleFeedTokenRepository.findAll()).thenReturn(List.of(later, sameTimeHigherId, first));
		when(scheduleFeedTokenJpoMapper.toDomain(later)).thenReturn(token("FEED-0002", 200L, "Later", "hash-2"));
		when(scheduleFeedTokenJpoMapper.toDomain(sameTimeHigherId))
			.thenReturn(token("FEED-0003", 100L, "Same time", "hash-3"));
		when(scheduleFeedTokenJpoMapper.toDomain(first)).thenReturn(token("FEED-0001", 100L, "First", "hash-1"));

		List<ScheduleFeedToken> result = scheduleFeedTokenStore.findAll();

		assertThat(result).extracting(ScheduleFeedToken::getId)
			.containsExactly("FEED-0001", "FEED-0003", "FEED-0002");
		verify(scheduleFeedTokenRepository).findAll();
		verify(scheduleFeedTokenJpoMapper).toDomain(later);
		verify(scheduleFeedTokenJpoMapper).toDomain(sameTimeHigherId);
		verify(scheduleFeedTokenJpoMapper).toDomain(first);
	}

	@Test
	void save_shouldMapPersistedTokenAndReturnDomain() {
		ScheduleFeedToken token = token("FEED-0001", 100L, "Google Calendar", "sha256-hex");
		ScheduleFeedTokenJpo tokenJpo = scheduleFeedTokenJpo("FEED-0001", 100L, "Google Calendar", "sha256-hex");
		ScheduleFeedTokenJpo savedJpo = scheduleFeedTokenJpo("FEED-0001", 100L, "Google Calendar", "sha256-hex");
		ScheduleFeedToken savedToken = token("FEED-0001", 100L, "Google Calendar", "sha256-hex");
		when(scheduleFeedTokenJpoMapper.toJpo(token)).thenReturn(tokenJpo);
		when(scheduleFeedTokenRepository.save(tokenJpo)).thenReturn(savedJpo);
		when(scheduleFeedTokenJpoMapper.toDomain(savedJpo)).thenReturn(savedToken);

		ScheduleFeedToken result = scheduleFeedTokenStore.save(token);

		assertThat(result).isSameAs(savedToken);
		verify(scheduleFeedTokenJpoMapper).toJpo(token);
		verify(scheduleFeedTokenRepository).save(tokenJpo);
		verify(scheduleFeedTokenJpoMapper).toDomain(savedJpo);
	}

	@Test
	void deleteById_shouldDeleteExistingToken() {
		ScheduleFeedTokenJpo tokenJpo = scheduleFeedTokenJpo("FEED-0001", 100L, "Google Calendar", "sha256-hex");
		when(scheduleFeedTokenRepository.findById("FEED-0001")).thenReturn(Optional.of(tokenJpo));

		scheduleFeedTokenStore.deleteById("FEED-0001");

		verify(scheduleFeedTokenRepository).findById("FEED-0001");
		verify(scheduleFeedTokenRepository).deleteById("FEED-0001");
	}

	@Test
	void deleteById_shouldThrowNotFoundWhenTokenDoesNotExist() {
		when(scheduleFeedTokenRepository.findById("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> scheduleFeedTokenStore.deleteById("missing"))
			.isInstanceOf(ScheduleFeedNotFoundException.class);

		verify(scheduleFeedTokenRepository).findById("missing");
		verify(scheduleFeedTokenRepository, never()).deleteById(anyString());
	}

	private ScheduleFeedTokenJpo scheduleFeedTokenJpo(
		String id,
		long registeredTime,
		String name,
		String tokenHash
	) {
		ScheduleFeedTokenJpo tokenJpo = new ScheduleFeedTokenJpo();
		tokenJpo.setId(id);
		tokenJpo.setRegisteredTime(registeredTime);
		tokenJpo.setName(name);
		tokenJpo.setTokenHash(tokenHash);
		return tokenJpo;
	}

	private ScheduleFeedToken token(String id, long registeredTime, String name, String tokenHash) {
		ScheduleFeedToken token = ScheduleFeedToken.builder()
			.name(name)
			.tokenHash(tokenHash)
			.build();
		token.setId(id);
		token.setRegisteredTime(registeredTime);
		return token;
	}
}
