package com.seoulchonnom.spec.house.facade.sdo;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AcquisitionTaxRdo {
	private BigDecimal lowRatePct;
	private long lowMaxManwon;
	private BigDecimal highRatePct;
	private long highMinManwon;
	private BigDecimal localEducationRatio;
}
