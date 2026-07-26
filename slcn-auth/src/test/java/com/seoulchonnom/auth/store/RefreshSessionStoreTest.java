package com.seoulchonnom.auth.store;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import com.seoulchonnom.aggregate.user.exception.InvalidRefreshTokenException;
import com.seoulchonnom.auth.store.projection.RefreshSession;

class RefreshSessionStoreTest {
	private static final String SESSION_KEY = "auth:refresh:session-1";

	@SuppressWarnings("unchecked")
	private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
	@SuppressWarnings("unchecked")
	private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
	@SuppressWarnings("unchecked")
	private final SetOperations<String, Object> setOperations = mock(SetOperations.class);
	private final RefreshSessionStore refreshSessionStore = new RefreshSessionStore(redisTemplate);

	@BeforeEach
	void setUp() {
		when(redisTemplate.<Object, Object>opsForHash()).thenReturn(hashOperations);
		when(redisTemplate.opsForSet()).thenReturn(setOperations);
	}

	@Test
	void save_shouldIndexSessionByUserWithSameTtl() {
		RefreshSession session = new RefreshSession("session-1", "USER-0001", "hash-value", 1000L, 2000L);
		Duration ttl = Duration.ofDays(14);

		refreshSessionStore.save(session, ttl);

		verify(hashOperations).putAll(eq(SESSION_KEY), anyMap());
		verify(redisTemplate).expire(SESSION_KEY, ttl);
		verify(setOperations).add("auth:refresh:user:USER-0001", "session-1");
		verify(redisTemplate).expire("auth:refresh:user:USER-0001", ttl);
	}

	@Test
	void findBySessionId_shouldReturnParsedSession() {
		when(hashOperations.entries(SESSION_KEY)).thenReturn(Map.of(
			"userId", "USER-0001",
			"refreshTokenHash", "hash-value",
			"issuedAt", "1000",
			"expiresAt", "2000"
		));

		Optional<RefreshSession> result = refreshSessionStore.findBySessionId("session-1");

		assertThat(result).isPresent();
		assertThat(result.get().userId()).isEqualTo("USER-0001");
		assertThat(result.get().refreshTokenHash()).isEqualTo("hash-value");
		assertThat(result.get().issuedAt()).isEqualTo(1000L);
		assertThat(result.get().expiresAt()).isEqualTo(2000L);
	}

	@Test
	void findBySessionId_shouldRejectCorruptedNumericField() {
		when(hashOperations.entries(SESSION_KEY)).thenReturn(Map.of(
			"userId", "USER-0001",
			"refreshTokenHash", "hash-value",
			"issuedAt", "not-a-number",
			"expiresAt", "2000"
		));

		assertThatThrownBy(() -> refreshSessionStore.findBySessionId("session-1"))
			.isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void findBySessionId_shouldReturnEmptyWhenSessionMissing() {
		when(hashOperations.entries(SESSION_KEY)).thenReturn(Map.of());

		assertThat(refreshSessionStore.findBySessionId("session-1")).isEmpty();
	}

	@Test
	void findBySessionId_shouldReturnEmptyWhenRequiredFieldMissing() {
		when(hashOperations.entries(SESSION_KEY)).thenReturn(Map.of(
			"userId", "USER-0001",
			"refreshTokenHash", "hash-value",
			"issuedAt", "1000"
		));

		assertThat(refreshSessionStore.findBySessionId("session-1")).isEmpty();
	}

	@Test
	void deleteAllByUserId_shouldDeleteEveryIndexedSessionAndIndex() {
		when(setOperations.members("auth:refresh:user:USER-0001"))
			.thenReturn(Set.of("session-1", "session-2"));

		refreshSessionStore.deleteAllByUserId("USER-0001");

		verify(redisTemplate).delete(argThat((List<String> keys) ->
			keys.containsAll(List.of(
				"auth:refresh:session-1",
				"auth:refresh:session-2",
				"auth:refresh:user:USER-0001"
			)) && keys.size() == 3));
	}
}
