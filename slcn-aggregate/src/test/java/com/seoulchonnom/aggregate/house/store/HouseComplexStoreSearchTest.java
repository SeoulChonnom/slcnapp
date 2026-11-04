package com.seoulchonnom.aggregate.house.store;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HouseComplexStoreSearchTest {
	@Test
	void toLikePattern_shouldRemoveSpacesAndLowercaseAndWrapWithWildcards() {
		assertThat(HouseComplexStore.toLikePattern("SK  VIEW")).isEqualTo("%skview%");
		assertThat(HouseComplexStore.toLikePattern("래미안 퍼스티지")).isEqualTo("%래미안퍼스티지%");
		assertThat(HouseComplexStore.toLikePattern("래미안\t퍼스티지\n")).isEqualTo("%래미안퍼스티지%");
	}

	@Test
	void toLikePattern_shouldEscapeLikeWildcardsSoPercentAloneDoesNotMatchEverything() {
		assertThat(HouseComplexStore.toLikePattern("%%")).isEqualTo("%\\%\\%%");
		assertThat(HouseComplexStore.toLikePattern("a_b")).isEqualTo("%a\\_b%");
		assertThat(HouseComplexStore.toLikePattern("a\\b")).isEqualTo("%a\\\\b%");
		assertThat(HouseComplexStore.toLikePattern("\\%_")).isEqualTo("%\\\\\\%\\_%");
	}
}
