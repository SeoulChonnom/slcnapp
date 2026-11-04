package com.seoulchonnom.spec.house.entity.vo;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 부대비용 계산 규칙. 중첩 VO까지 모두 equals를 가진다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CostRates {
	private AcquisitionTax acquisitionTax;
	private FirstTimeRelief firstTimeRelief;
	private Brokerage brokerage;
	private BigDecimal registrationRatePct;
}
