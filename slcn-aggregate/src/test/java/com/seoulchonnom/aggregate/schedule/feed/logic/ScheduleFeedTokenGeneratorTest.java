package com.seoulchonnom.aggregate.schedule.feed.logic;

import static org.assertj.core.api.Assertions.*;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class ScheduleFeedTokenGeneratorTest {
	private final ScheduleFeedTokenGenerator generator = new ScheduleFeedTokenGenerator();

	@Test
	void generate_shouldReturn32RandomBytesAsUnpaddedUrlSafeBase64() {
		String token = generator.generate();

		assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
		assertThat(token).doesNotContain("=");
		assertThat(token).matches("[A-Za-z0-9_-]+");
	}

	@Test
	void generate_shouldProduceDifferentValuesForSuccessiveCalls() {
		assertThat(generator.generate()).isNotEqualTo(generator.generate());
	}
}
