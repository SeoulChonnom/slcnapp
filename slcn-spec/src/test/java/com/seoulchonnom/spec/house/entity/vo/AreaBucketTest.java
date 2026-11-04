package com.seoulchonnom.spec.house.entity.vo;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AreaBucketTest {
	@ParameterizedTest
	@CsvSource({
		"54.99, OTHER",
		"55.00, B59",
		"64.99, B59",
		"65.00, OTHER",
		"79.99, OTHER",
		"80.00, B84",
		"89.99, B84",
		"90.00, OTHER"
	})
	void of_shouldJudgeBoundaries(String area, AreaBucket expected) {
		assertThat(AreaBucket.of(new BigDecimal(area))).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({"84.9000, B84", "59, B59"})
	void of_shouldIgnoreScale(String area, AreaBucket expected) {
		assertThat(AreaBucket.of(new BigDecimal(area))).isEqualTo(expected);
	}
}
