package com.seoulchonnom.aggregate.house.collect;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ComplexNameNormalizerTest {
	@Test
	void normalize_shouldRemoveSpacesParenthesesAndApartWord() {
		assertThat(ComplexNameNormalizer.normalize("래미안 퍼스티지 (1단지) 아파트")).isEqualTo("래미안퍼스티지");
	}

	@Test
	void normalize_shouldLowercaseEnglish() {
		assertThat(ComplexNameNormalizer.normalize("SK VIEW")).isEqualTo("skview");
	}

	@Test
	void normalize_shouldTreatNullAsEmpty() {
		assertThat(ComplexNameNormalizer.normalize(null)).isEmpty();
	}

	@Test
	void alternativeAptSeq_shouldBeSameForNamesThatNormalizeEqually() {
		String a = ComplexNameNormalizer.alternativeAptSeq("11680", "10300", "0652", "0000", "개포 우성3차 (본동)");
		String b = ComplexNameNormalizer.alternativeAptSeq("11680", "10300", "0652", "0000", "개포우성3차아파트");

		assertThat(a).isEqualTo(b).startsWith("ALT:116801030006520000:").hasSize("ALT:116801030006520000:".length() + 8);
	}

	@Test
	void alternativeAptSeq_shouldDifferForDifferentNames() {
		assertThat(ComplexNameNormalizer.alternativeAptSeq("11680", "10300", "0652", "0000", "가"))
			.isNotEqualTo(ComplexNameNormalizer.alternativeAptSeq("11680", "10300", "0652", "0000", "나"));
	}
}
