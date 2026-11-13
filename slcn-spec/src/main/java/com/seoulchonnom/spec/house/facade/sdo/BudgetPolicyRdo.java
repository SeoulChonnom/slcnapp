package com.seoulchonnom.spec.house.facade.sdo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BudgetPolicyRdo {
	private String policyId;
	/** yyyy-MM-dd. 다른 Rdo처럼 날짜는 문자열로 내려 직렬화 설정에 기대지 않는다. */
	private String effectiveFrom;
	private BigDecimal dsrRatio;
	private BigDecimal stressRatePp;
	private int termYears;
	private long defaultReserveManwon;
	/** FIRST_TIME, NON_FIRST 두 키. */
	private Map<String, BigDecimal> ltv;
	private List<PriceCapRdo> priceCaps;
	private CostRatesRdo costRates;
	private String sourceNote;
}
