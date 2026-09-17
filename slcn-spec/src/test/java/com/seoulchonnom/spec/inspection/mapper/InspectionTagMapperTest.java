package com.seoulchonnom.spec.inspection.mapper;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InspectionTagMapperTest {
	private final InspectionTagMapper inspectionTagMapper = new InspectionTagMapper();

	@Test
	void normalizeName_shouldStripHashAndCollapseWhitespace() {
		assertThat(inspectionTagMapper.normalizeName("  #직주 근접  ")).isEqualTo("직주 근접");
		assertThat(inspectionTagMapper.normalizeName("##한강")).isEqualTo("한강");
	}

	@Test
	void normalizeName_shouldReturnNullForEmptyResult() {
		assertThat(inspectionTagMapper.normalizeName("   ")).isNull();
		assertThat(inspectionTagMapper.normalizeName("#")).isNull();
		assertThat(inspectionTagMapper.normalizeName(null)).isNull();
	}

	@Test
	void normalizeName_shouldKeepCaseAsWritten() {
		assertThat(inspectionTagMapper.normalizeName("#Riverside")).isEqualTo("Riverside");
	}

	@Test
	void toInspectionTag_shouldReturnNullWhenNameNormalizesToNothing() {
		assertThat(inspectionTagMapper.toInspectionTag("#")).isNull();
		assertThat(inspectionTagMapper.toInspectionTag("# 한강 ").getName()).isEqualTo("한강");
	}
}
