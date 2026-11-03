package com.seoulchonnom.spec.house.entity.vo;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 취득세율. lowMax 이하는 낮은 세율, highMin 이상은 높은 세율, 사이는 선형 보간한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AcquisitionTax {
	private BigDecimal lowRatePct;
	private long lowMaxManwon;
	private BigDecimal highRatePct;
	private long highMinManwon;
	private BigDecimal localEducationRatio;
}
