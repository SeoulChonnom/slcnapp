package com.seoulchonnom.spec.house.constant;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class HouseConstantTest {
	@Test
	void districts_shouldHave25DistinctSortedCodes() {
		List<String> codes = HouseConstant.DISTRICTS.stream().map(HouseConstant.District::lawdCd).toList();

		assertThat(codes).hasSize(25).doesNotHaveDuplicates().isSorted();
	}

	@Test
	void districtName_shouldResolveKnownCodeAndReturnNullForUnknown() {
		assertThat(HouseConstant.districtName("11680")).isEqualTo("강남구");
		assertThat(HouseConstant.districtName("99999")).isNull();
		assertThat(HouseConstant.isDistrict("11110")).isTrue();
		assertThat(HouseConstant.isDistrict(null)).isFalse();
	}

	@Test
	void lawdCdOf_shouldResolveDistrictNameAndReturnNullForUnknown() {
		assertThat(HouseConstant.lawdCdOf("강남구")).isEqualTo("11680");
		assertThat(HouseConstant.lawdCdOf("종로구")).isEqualTo("11110");
		assertThat(HouseConstant.lawdCdOf("강남")).isNull();
		assertThat(HouseConstant.lawdCdOf(null)).isNull();
		assertThat(HouseConstant.DISTRICTS).allSatisfy(
			district -> assertThat(HouseConstant.lawdCdOf(district.name())).isEqualTo(district.lawdCd()));
	}
}
