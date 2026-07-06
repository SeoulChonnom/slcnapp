package com.seoulchonnom.auth.store;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import com.seoulchonnom.aggregate.user.exception.InvalidRefreshTokenException;
import com.seoulchonnom.auth.store.projection.RefreshSession;

class RefreshSessionStoreTest {
	private static final String SESSION_KEY = "auth:refresh:session-1";

	@SuppressWarnings("unchecked")
	private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
	@SuppressWarnings("unchecked")
	private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
	private final RefreshSessionStore refreshSessionStore = new RefreshSessionStore(redisTemplate);

	@BeforeEach
	void setUp() {
		when(redisTemplate.<Object, Object>opsForHash()).thenReturn(hashOperations);
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
}
