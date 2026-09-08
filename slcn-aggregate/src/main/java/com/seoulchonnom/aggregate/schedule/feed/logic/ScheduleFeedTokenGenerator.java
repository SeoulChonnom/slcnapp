package com.seoulchonnom.aggregate.schedule.feed.logic;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class ScheduleFeedTokenGenerator {
	private static final int TOKEN_BYTE_LENGTH = 32;

	private final SecureRandom secureRandom;

	public ScheduleFeedTokenGenerator() {
		this(new SecureRandom());
	}

	ScheduleFeedTokenGenerator(SecureRandom secureRandom) {
		this.secureRandom = secureRandom;
	}

	public String generate() {
		byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
