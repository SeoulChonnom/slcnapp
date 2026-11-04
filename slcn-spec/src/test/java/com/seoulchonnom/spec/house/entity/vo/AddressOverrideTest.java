package com.seoulchonnom.spec.house.entity.vo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AddressOverrideTest {
	@Test
	void equals_shouldBeTrueForSameValues() {
		AddressOverride a = new AddressOverride("서울특별시 강남구 개포로 307", "0652", null);
		AddressOverride b = new AddressOverride("서울특별시 강남구 개포로 307", "0652", null);

		assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
	}

	@Test
	void equals_shouldBeFalseWhenAnyFieldDiffers() {
		assertThat(new AddressOverride("a", "1", null)).isNotEqualTo(new AddressOverride("a", "1", "2"));
	}
}
