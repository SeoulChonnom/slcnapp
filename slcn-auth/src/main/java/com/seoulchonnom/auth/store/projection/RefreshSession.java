package com.seoulchonnom.auth.store.projection;

public record RefreshSession(
	String sessionId,
	String userId,
	String refreshTokenHash,
	long issuedAt,
	long expiresAt
) {
}
