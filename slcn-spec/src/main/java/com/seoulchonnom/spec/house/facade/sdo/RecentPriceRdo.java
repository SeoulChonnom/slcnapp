package com.seoulchonnom.spec.house.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 최근 실거래가. 3개월 평균, 거래가 없으면 6개월 평균이다. 둘 다 없으면 이 객체 자체를 내리지 않는다(null).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecentPriceRdo {
	private long priceManwon;
	/** 3 또는 6 */
	private int windowMonths;
	private int sample;
}
