package com.seoulchonnom.spec.schedule.feed.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class ScheduleFeedTokenSerializationTest {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void jacksonSerialization_shouldNeverExposeTokenHash() throws Exception {
		ScheduleFeedToken token = token();

		String json = objectMapper.writeValueAsString(token);

		assertThat(json).contains("\"name\":\"Calendar\"");
		assertThat(json).doesNotContain("tokenHash").doesNotContain("hash-secret");
	}

	@Test
	void toString_shouldNeverExposeTokenHash() {
		ScheduleFeedToken token = token();

		assertThat(token.toString()).doesNotContain("hash-secret");
	}

	private ScheduleFeedToken token() {
		ScheduleFeedToken token = ScheduleFeedToken.builder()
			.name("Calendar")
			.tokenHash("hash-secret")
			.build();
		token.setId("FEED-0001");
		return token;
	}
}
