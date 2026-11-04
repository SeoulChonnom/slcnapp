package com.seoulchonnom.spec.house.entity.vo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RedevelopmentVocabularyTest {
	@Test
	void projectType_shouldIgnoreAllWhitespaceAndReturnStandardLabel() {
		assertThat(RedevelopmentProjectType.parse("주택정비형재개발").orElseThrow().label()).isEqualTo("주택정비형 재개발");
		assertThat(RedevelopmentProjectType.parse(" 주택정비형  재개발 ").orElseThrow().label()).isEqualTo("주택정비형 재개발");
		assertThat(RedevelopmentProjectType.parse("아파트지구 재건축").orElseThrow().label()).isEqualTo("아파트지구 재건축");
	}

	@Test
	void projectType_shouldRejectUnknownAndBlank() {
		assertThat(RedevelopmentProjectType.parse("재개발")).isEmpty();
		assertThat(RedevelopmentProjectType.parse("-")).isEmpty();
		assertThat(RedevelopmentProjectType.parse(null)).isEmpty();
	}

	@Test
	void stage_shouldIgnoreWhitespaceAndRejectUnknown() {
		assertThat(RedevelopmentStage.parse("관리 처분").orElseThrow().label()).isEqualTo("관리처분");
		assertThat(RedevelopmentStage.parse("착공").orElseThrow().label()).isEqualTo("착공");
		assertThat(RedevelopmentStage.parse("입주")).isEmpty();
		assertThat(RedevelopmentStage.parse("")).isEmpty();
	}
}
