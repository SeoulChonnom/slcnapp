package com.seoulchonnom.spec.house.facade.sdo;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BrokerageRdo {
	private List<BrokerageTierRdo> tiers;
	private BigDecimal vatRatio;
}
