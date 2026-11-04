package com.seoulchonnom.spec.house.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComplexSummaryItemRdo {
	/** DISTRICT는 lawdCd(5자리), DONG은 lawdCd + umdCd(10자리) */
	private String code;
	private String name;
	private String lawdCd;
	private String districtName;
	private long count;
	private double centerLat;
	private double centerLng;
}
