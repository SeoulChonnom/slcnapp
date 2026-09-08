package com.seoulchonnom.aggregate.schedule.feed.logic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

@Component
public class ScheduleFeedTokenHasher {
	public String hash(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			throw new IllegalArgumentException("rawToken은 필수입니다.");
		}

		try {
			MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
			byte[] digest = messageDigest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 algorithm is not available.", exception);
		}
	}
}
