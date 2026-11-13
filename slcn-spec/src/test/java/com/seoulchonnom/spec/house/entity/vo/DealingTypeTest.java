package com.seoulchonnom.spec.house.entity.vo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DealingTypeTest {
	@Test
	void ofLabel_shouldMapKnownLabelsAndFallBackToUnknown() {
		assertThat(DealingType.ofLabel("중개거래")).isEqualTo(DealingType.BROKER);
		assertThat(DealingType.ofLabel(" 직거래 ")).isEqualTo(DealingType.DIRECT);
		assertThat(DealingType.ofLabel(null)).isEqualTo(DealingType.UNKNOWN);
		assertThat(DealingType.ofLabel("")).isEqualTo(DealingType.UNKNOWN);
		assertThat(DealingType.ofLabel("기타")).isEqualTo(DealingType.UNKNOWN);
	}
}
