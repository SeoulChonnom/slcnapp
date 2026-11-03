package com.seoulchonnom.spec.house.entity.vo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SampleStatusTest {
	@ParameterizedTest
	@CsvSource({
		"0, 5, INSUFFICIENT",
		"1, 5, INSUFFICIENT",
		"2, 5, LOW",
		"3, 2, LOW",
		"3, 3, OK"
	})
	void of_shouldUseSmallerCount(int cntFrom, int cntTo, SampleStatus expected) {
		assertThat(SampleStatus.of(cntFrom, cntTo)).isEqualTo(expected);
	}
}
