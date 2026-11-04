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
public class CostRatesRdo {
	private AcquisitionTaxRdo acquisitionTax;
	private FirstTimeReliefRdo firstTimeRelief;
	private BrokerageRdo brokerage;
	private BigDecimal registrationRatePct;
}
