package com.seoulchonnom.auth.store;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.user.exception.InvalidRefreshTokenException;
import com.seoulchonnom.auth.store.projection.RefreshSession;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RefreshSessionStore {
	private static final String KEY_PREFIX = "auth:refresh:";
	private static final String USER_ID_FIELD = "userId";
	private static final String REFRESH_TOKEN_HASH_FIELD = "refreshTokenHash";
	private static final String ISSUED_AT_FIELD = "issuedAt";
	private static final String EXPIRES_AT_FIELD = "expiresAt";

	private final RedisTemplate<String, Object> redisTemplate;

	public void save(RefreshSession refreshSession, Duration ttl) {
		String key = key(refreshSession.sessionId());
		redisTemplate.opsForHash().putAll(key, Map.of(
			USER_ID_FIELD, refreshSession.userId(),
			REFRESH_TOKEN_HASH_FIELD, refreshSession.refreshTokenHash(),
			ISSUED_AT_FIELD, String.valueOf(refreshSession.issuedAt()),
			EXPIRES_AT_FIELD, String.valueOf(refreshSession.expiresAt())
		));
		redisTemplate.expire(key, ttl);
	}

	public Optional<RefreshSession> findBySessionId(String sessionId) {
		Map<Object, Object> entries = redisTemplate.opsForHash().entries(key(sessionId));
		if (entries.isEmpty()) {
			return Optional.empty();
		}

		Object userId = entries.get(USER_ID_FIELD);
		Object refreshTokenHash = entries.get(REFRESH_TOKEN_HASH_FIELD);
		Object issuedAt = entries.get(ISSUED_AT_FIELD);
		Object expiresAt = entries.get(EXPIRES_AT_FIELD);
		if (userId == null || refreshTokenHash == null || issuedAt == null || expiresAt == null) {
			return Optional.empty();
		}

		return Optional.of(new RefreshSession(
			sessionId,
			String.valueOf(userId),
			String.valueOf(refreshTokenHash),
			parseEpochMillis(ISSUED_AT_FIELD, issuedAt),
			parseEpochMillis(EXPIRES_AT_FIELD, expiresAt)
		));
	}

	private long parseEpochMillis(String field, Object value) {
		try {
			return Long.parseLong(String.valueOf(value));
		} catch (NumberFormatException e) {
			log.warn("Invalid refresh session field detected: field={}", field);
			throw new InvalidRefreshTokenException();
		}
	}

	public void delete(String sessionId) {
		redisTemplate.delete(key(sessionId));
	}

	private String key(String sessionId) {
		return KEY_PREFIX + sessionId;
	}
}
