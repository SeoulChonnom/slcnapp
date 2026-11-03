package com.seoulchonnom.spec.house.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.house.entity.vo.CostRates;
import com.seoulchonnom.spec.house.entity.vo.PriceCap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 시행일별 예산 계산 규칙. 수동 SQL로만 추가하고, 오늘 기준 가장 최근 시행일 행 하나를 쓴다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class HouseBudgetPolicy extends DomainEntity {
	private LocalDate effectiveFrom;
	private BigDecimal dsrRatio;
	private BigDecimal stressRatePp;
	private int termYears;
	private long defaultReserveManwon;
	private BigDecimal ltvFirstTime;
	private BigDecimal ltvNonFirst;
	private List<PriceCap> priceCaps;
	private CostRates costRates;
	private String sourceNote;
}
