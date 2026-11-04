package com.seoulchonnom.spec.house.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComplexMapItemRdo {
	private String complexId;
	private String name;
	private String lawdCd;
	private String districtName;
	private String umdName;
	private Double latitude;
	private Double longitude;
	/** 최근 6개월에도 거래가 없으면 null */
	private RecentPriceRdo recentPrice;
	private Integer buildYear;
	private Integer households;
}
