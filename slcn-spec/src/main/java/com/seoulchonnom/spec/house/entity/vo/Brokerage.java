package com.seoulchonnom.spec.house.entity.vo;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Brokerage {
	private List<BrokerageTier> tiers;
	private BigDecimal vatRatio;
}
