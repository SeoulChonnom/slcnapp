package com.seoulchonnom.aggregate.house.store.jpo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;
import com.seoulchonnom.aggregate.house.store.jpo.converter.CostRatesConverter;
import com.seoulchonnom.aggregate.house.store.jpo.converter.PriceCapListConverter;
import com.seoulchonnom.spec.house.entity.vo.CostRates;
import com.seoulchonnom.spec.house.entity.vo.PriceCap;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "house_budget_policy", schema = "slcn",
	uniqueConstraints = @UniqueConstraint(name = "uk_house_budget_policy_effective_from",
		columnNames = "effective_from"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HouseBudgetPolicyJpo extends DomainEntityJpo {
	@Column(nullable = false)
	private LocalDate effectiveFrom;

	@Column(nullable = false, precision = 4, scale = 3)
	private BigDecimal dsrRatio;

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal stressRatePp;

	@Column(nullable = false)
	private int termYears;

	@Column(nullable = false)
	private long defaultReserveManwon;

	@Column(nullable = false, precision = 4, scale = 3)
	private BigDecimal ltvFirstTime;

	@Column(nullable = false, precision = 4, scale = 3)
	private BigDecimal ltvNonFirst;

	@Column(columnDefinition = "TEXT", nullable = false)
	@Convert(converter = PriceCapListConverter.class)
	private List<PriceCap> priceCaps;

	@Column(columnDefinition = "TEXT", nullable = false)
	@Convert(converter = CostRatesConverter.class)
	private CostRates costRates;

	@Column(length = 500)
	private String sourceNote;
}
