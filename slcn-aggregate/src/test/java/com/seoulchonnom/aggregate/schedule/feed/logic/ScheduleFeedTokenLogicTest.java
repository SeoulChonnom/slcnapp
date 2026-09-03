package com.seoulchonnom.aggregate.schedule.feed.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.schedule.feed.exception.ScheduleFeedNotFoundException;
import com.seoulchonnom.aggregate.schedule.feed.store.ScheduleFeedTokenStore;
import com.seoulchonnom.spec.schedule.feed.entity.ScheduleFeedToken;

class ScheduleFeedTokenLogicTest {
	private final ScheduleFeedTokenStore store = mock(ScheduleFeedTokenStore.class);
	private final ScheduleFeedTokenHasher hasher = mock(ScheduleFeedTokenHasher.class);
	private final ScheduleFeedTokenGenerator generator = mock(ScheduleFeedTokenGenerator.class);
	private final ScheduleFeedTokenLogic logic = new ScheduleFeedTokenLogic(store, hasher, generator);

	@Test
	void create_shouldRejectBlankNameBeforeGeneratingOrSaving() {
		assertThatThrownBy(() -> logic.create(" \t"))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("name은 필수입니다.");

		verifyNoInteractions(store, hasher, generator);
	}

	@Test
	void create_shouldRejectNameLongerThan100CharactersBeforeGeneratingOrSaving() {
		String name = "가".repeat(101);

		assertThatThrownBy(() -> logic.create(name))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("name은 100자 이하여야 합니다.");

		verifyNoInteractions(store, hasher, generator);
	}

	@Test
	void create_shouldPersistOnlyHashAndReturnRawTokenOnce() {
		String rawToken = "raw-token";
		String tokenHash = "a".repeat(64);
		ScheduleFeedToken savedToken = token("FEED-0001", "Google Calendar", tokenHash);
		when(generator.generate()).thenReturn(rawToken);
		when(hasher.hash(rawToken)).thenReturn(tokenHash);
		when(store.save(any(ScheduleFeedToken.class))).thenReturn(savedToken);

		ScheduleFeedTokenLogic.CreatedFeedToken created = logic.create("Google Calendar");

		assertThat(created.feedToken()).isSameAs(savedToken);
		assertThat(created.rawToken()).isEqualTo(rawToken);
		ArgumentCaptor<ScheduleFeedToken> captor = ArgumentCaptor.forClass(ScheduleFeedToken.class);
		verify(store).save(captor.capture());
		assertThat(captor.getValue().getName()).isEqualTo("Google Calendar");
		assertThat(captor.getValue().getTokenHash()).isEqualTo(tokenHash);
		assertThat(captor.getValue().getTokenHash()).isNotEqualTo(rawToken);
		verify(generator).generate();
		verify(hasher).hash(rawToken);
	}

	@Test
	void getAll_shouldDelegateToStoreWithoutCreatingOrExposingRawToken() {
		ScheduleFeedToken first = token("FEED-0001", "Google Calendar", "a".repeat(64));
		ScheduleFeedToken second = token("FEED-0002", "Apple Calendar", "b".repeat(64));
		when(store.findAll()).thenReturn(List.of(first, second));

		List<ScheduleFeedToken> result = logic.getAll();

		assertThat(result).containsExactly(first, second);
		assertThat(result).extracting(ScheduleFeedToken::getTokenHash)
			.containsExactly("a".repeat(64), "b".repeat(64));
		verify(store).findAll();
		verifyNoInteractions(hasher, generator);
	}

	@Test
	void delete_shouldDelegateByFeedId() {
		logic.delete("FEED-0001");

		verify(store).deleteById("FEED-0001");
		verifyNoInteractions(hasher, generator);
	}

	@Test
	void validate_shouldHashBeforeLookingUpTheToken() {
		String rawToken = "raw-token";
		String tokenHash = "c".repeat(64);
		when(hasher.hash(rawToken)).thenReturn(tokenHash);
		when(store.findByTokenHash(tokenHash)).thenReturn(Optional.of(token("FEED-0001", "Calendar", tokenHash)));

		logic.validate(rawToken);

		InOrder inOrder = inOrder(hasher, store);
		inOrder.verify(hasher).hash(rawToken);
		inOrder.verify(store).findByTokenHash(tokenHash);
	}

	@Test
	void validate_shouldRejectUnknownHashWithUniformNotFoundException() {
		String rawToken = "unknown-token";
		String tokenHash = "d".repeat(64);
		when(hasher.hash(rawToken)).thenReturn(tokenHash);
		when(store.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> logic.validate(rawToken))
			.isInstanceOf(ScheduleFeedNotFoundException.class)
			.hasMessage("해당 일정 피드가 없습니다.")
			.hasMessageNotContaining(rawToken);
	}

	@Test
	void validate_shouldRejectBlankAndMalformedRawTokensWithoutLeakingValue() {
		String malformedToken = "bad\nfeed-token";

		assertThatThrownBy(() -> logic.validate(" "))
			.isInstanceOf(ScheduleFeedNotFoundException.class)
			.hasMessage("해당 일정 피드가 없습니다.");
		assertThatThrownBy(() -> logic.validate(malformedToken))
			.isInstanceOf(ScheduleFeedNotFoundException.class)
			.hasMessage("해당 일정 피드가 없습니다.")
			.hasMessageNotContaining(malformedToken);

		verifyNoInteractions(store);
	}

	@Test
	void validate_shouldTranslateHasherRejectionToUniformNotFoundException() {
		when(hasher.hash("malformed-token")).thenThrow(new IllegalArgumentException("invalid token"));

		assertThatThrownBy(() -> logic.validate("malformed-token"))
			.isInstanceOf(ScheduleFeedNotFoundException.class)
			.hasMessage("해당 일정 피드가 없습니다.");

		verifyNoInteractions(store);
	}

	private ScheduleFeedToken token(String id, String name, String tokenHash) {
		ScheduleFeedToken token = ScheduleFeedToken.builder()
			.name(name)
			.tokenHash(tokenHash)
			.build();
		token.setId(id);
		return token;
	}
}
