package com.seoulchonnom.spec.house.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComplexCardRdo {
	private String complexId;
	private String name;
	private String districtName;
	private String umdName;
	/** 좌표 보강 전이면 null */
	private Double latitude;
	private Double longitude;
	private Integer buildYear;
	private Integer households;
	/** 통계 행이 없거나 최근 6개월 거래가 없으면 null */
	private RecentPriceRdo recentPrice;
}
