package com.seoulchonnom.spec.house.entity.vo;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class CostRatesTest {
	private CostRates sample(long cap) {
		return new CostRates(
			new AcquisitionTax(new BigDecimal("1.0"), 60000, new BigDecimal("3.0"), 90000, new BigDecimal("0.1")),
			new FirstTimeRelief(200, 120000),
			new Brokerage(List.of(new BrokerageTier(5000L, new BigDecimal("0.6"), cap),
				new BrokerageTier(null, new BigDecimal("0.7"), null)), new BigDecimal("0.1")),
			new BigDecimal("0.2"));
	}

	@Test
	void equals_shouldBeTrueForSameValuesIncludingNested() {
		assertThat(sample(25)).isEqualTo(sample(25)).hasSameHashCodeAs(sample(25));
	}

	@Test
	void equals_shouldBeFalseWhenNestedValueDiffers() {
		assertThat(sample(25)).isNotEqualTo(sample(26));
	}

	@Test
	void priceCapEquals_shouldCompareNullableMax() {
		assertThat(new PriceCap(null, 20000)).isEqualTo(new PriceCap(null, 20000)).hasSameHashCodeAs(new PriceCap(null, 20000));
		assertThat(new PriceCap(150000L, 60000)).isNotEqualTo(new PriceCap(null, 60000));
	}
}
