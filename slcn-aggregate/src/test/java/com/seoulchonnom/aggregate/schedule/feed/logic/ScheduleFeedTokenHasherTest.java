package com.seoulchonnom.aggregate.schedule.feed.logic;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ScheduleFeedTokenHasherTest {
	private final ScheduleFeedTokenHasher hasher = new ScheduleFeedTokenHasher();

	@Test
	void hash_shouldReturnLowercaseSha256Hex() {
		assertThat(hasher.hash("known-token"))
			.isEqualTo("49e2e40e591e61357758299c8cee170fb9fa7da160ec8acf110a4a409d905aaf");
	}

	@Test
	void hash_shouldRejectBlankTokenWithoutEchoingItsValue() {
		assertThatThrownBy(() -> hasher.hash(" \t\n"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("rawToken은 필수입니다.")
			.hasMessageNotContaining("\\t")
			.hasMessageNotContaining("\\n");
	}

	@Test
	void hash_shouldRejectNullToken() {
		assertThatThrownBy(() -> hasher.hash(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("rawToken은 필수입니다.");
	}
}
