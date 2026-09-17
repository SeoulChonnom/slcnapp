package com.seoulchonnom.aggregate.file.storage;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ObjectKeysTest {
	private static final String UUID_NAME = "72d768d4-2b05-48f9-bee8-fee3b52e909f";

	@Test
	void original_shouldPlaceFileUnderOriginalsPrefix() {
		assertThat(ObjectKeys.original("travel", UUID_NAME + ".png"))
			.isEqualTo("originals/travel/" + UUID_NAME + ".png");
	}

	@Test
	void derived_shouldPlaceFileUnderDerivedPrefix() {
		assertThat(ObjectKeys.derived("travel", UUID_NAME + "_home-thumb.webp"))
			.isEqualTo("derived/travel/" + UUID_NAME + "_home-thumb.webp");
	}

	@Test
	void of_shouldChoosePrefixByVariantSuffix() {
		assertThat(ObjectKeys.of("travel", UUID_NAME + ".png"))
			.isEqualTo("originals/travel/" + UUID_NAME + ".png");
		assertThat(ObjectKeys.of("travel", UUID_NAME + "_home-feature.webp"))
			.isEqualTo("derived/travel/" + UUID_NAME + "_home-feature.webp");
	}

	@Test
	void of_shouldTreatUnknownShapesAsOriginalSoValidationStaysTheSingleGate() {
		assertThat(ObjectKeys.of("travel", "not-a-uuid.png"))
			.isEqualTo("originals/travel/not-a-uuid.png");
	}

	@Test
	void isDerived_shouldReflectThePrefix() {
		assertThat(ObjectKeys.isDerived("derived/travel/" + UUID_NAME + "_home-thumb.webp")).isTrue();
		assertThat(ObjectKeys.isDerived("originals/travel/" + UUID_NAME + ".png")).isFalse();
	}
}
